"""
Ice Boat Autopilot Planner
Reads ice blocks + start/target from stdin JSON, outputs trajectory + actions.

Pipeline:
  1. Compute temp_target (start→target line ∩ loaded chunk boundary)
  2. Extract road centerline (greedy ice follow toward target)
  3. Fit B-spline curve
  4. Compute yaw + curvature at each trajectory point
  5. Bake action keyframes (geometry-based, single-pass)

Physics model:
  BoatEntity.updateVelocity():
    vel *= drag          ← drag=0.98 (ice) / 0.989 (blue_ice)
    yawVel += playerInput   ← our action: LEFT=-1, RIGHT=+1, STRAIGHT=0
    yaw += yawVel
    vel += forwardVector * 0.04   ← always applied (full throttle)
    pos += vel

No speed cap — terminal speed = 0.04 / (1 - drag) is natural equilibrium.
yawVel NEVER dragged — only modified by paddle ±1 per tick.
"""

import json
import math
import sys


# ── Vanilla Boat Physics ──────────────────────────────────────────────
FORWARD_FORCE = 0.04
TERMINAL_SPEEDS = {0.98: 2.0, 0.989: 3.636}


def terminal_speed(drag: float) -> float:
    return FORWARD_FORCE / (1.0 - drag)


# ── Geometry Helpers ──────────────────────────────────────────────────

def dist(a, b):
    """Distance between two points, each can be (x,z) tuple or {x,z} dict."""
    if isinstance(a, dict):
        ax, az = a['x'], a['z']
    elif isinstance(a, (list, tuple)):
        ax, az = a[0], a[1]
    else:
        ax, az = a.x, a.z
    if isinstance(b, dict):
        bx, bz = b['x'], b['z']
    elif isinstance(b, (list, tuple)):
        bx, bz = b[0], b[1]
    else:
        bx, bz = b.x, b.z
    return math.sqrt((ax - bx) ** 2 + (az - bz) ** 2)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1]


def normalize_angle(a):
    while a > 180:
        a -= 360
    while a < -180:
        a += 360
    return a


# ── Step 1: Temp Target ───────────────────────────────────────────────

def compute_temp_target(start, target, loaded_range):
    """
    Find the farthest point along start→target that is within loaded chunks.
    Intersection of line segment with axis-aligned bounding box.
    Returns the last point on the segment before it exits the loaded range.
    """
    sx, sz = start['x'], start['z']
    tx, tz = target['x'], target['z']
    dx, dz = tx - sx, tz - sz

    if abs(dx) < 1e-6 and abs(dz) < 1e-6:
        return (tx, tz)

    lr = loaded_range
    min_x, max_x = lr['min_x'], lr['max_x']
    min_z, max_z = lr['min_z'], lr['max_z']

    # Clip to loaded chunk boundary using Liang-Barsky
    p = [-dx, dx, -dz, dz]
    q = [sx - min_x, max_x - sx, sz - min_z, max_z - sz]
    t_min, t_max = 0.0, 1.0

    for pi, qi in zip(p, q):
        if abs(pi) < 1e-6:
            if qi < 0:
                # Outside
                break
        else:
            t = qi / pi
            if pi < 0:
                t_min = max(t_min, t)
            else:
                t_max = min(t_max, t)

    t_max = max(0.0, min(1.0, t_max))
    return (sx + dx * t_max, sz + dz * t_max)


# ── Step 2: Centerline Extraction ─────────────────────────────────────

def extract_centerline(ice_blocks, start, target, ice_y):
    """
    Bi-directional slice-median centerline with corner splitting.

    For multi-block-wide ice roads, compute the road center by taking
    the median coordinate of orthogonal slices.  Detects corners where
    the median jumps and splits the road into segments for clean results.

    Returns list of {'x','z'} points along the road center.
    """
    if not ice_blocks:
        return []

    ice_set = set()
    for b in ice_blocks:
        if isinstance(b, dict):
            bx, bz = int(b.get('x', 0)), int(b.get('z', 0))
            by = b.get('y', ice_y)
        elif isinstance(b, (list, tuple)):
            if len(b) == 3:
                bx, bz, by = int(b[0]), int(b[2]), b[1]
            else:
                bx, bz = int(b[0]), int(b[1])
                by = ice_y
        else:
            continue
        if by != ice_y:
            continue
        ice_set.add((bx, bz))

    if not ice_set:
        return []

    # Determine primary axis
    xs = sorted(p[0] for p in ice_set)
    zs = sorted(p[1] for p in ice_set)
    rx = max(xs) - min(xs)
    rz = max(zs) - min(zs)

    def road_center(vals):
        """Return the midpoint of the block strip (true road center).
           For even-width roads (2 blocks), this gives the centerline between blocks.
           For odd-width roads, this gives the center block's center."""
        return (min(vals) + max(vals)) / 2.0

    def slice_by_x(blk_set):
        by_x = {}
        for bx, bz in blk_set:
            by_x.setdefault(bx, []).append(bz)
        out = []
        for bx in sorted(by_x):
            out.append({'x': bx + 0.5, 'z': road_center(by_x[bx])})
        return out

    def slice_by_z(blk_set):
        by_z = {}
        for bx, bz in blk_set:
            by_z.setdefault(bz, []).append(bx)
        out = []
        for bz in sorted(by_z):
            out.append({'x': road_center(by_z[bz]), 'z': bz + 0.5})
        return out

    def find_jump(pts):
        """Return index of first jump >4.0 between adjacent points, or -1.

        Threshold 4.0 (not 1.5): a real road corner is a discrete turn where
        the centerline jumps several blocks and then settles.  A gradual ramp
        / ice sheet shifts the slice-median by only ~0.5-2.0 per slice, so a
        1.5 threshold mis-detects those as corners and corner-split chops the
        whole sheet down to a tiny corner stub (trajectory[0] lands tens of
        blocks from the boat → instant "deviated")."""
        for i in range(len(pts) - 1):
            if abs(pts[i + 1]['z'] - pts[i]['z']) > 4.0:
                return i
            if abs(pts[i + 1]['x'] - pts[i]['x']) > 4.0:
                return i
        return -1

    # Compute primary-axis centerline
    if rx >= rz:
        pts = slice_by_x(ice_set)
    else:
        pts = slice_by_z(ice_set)

    # If a jump exists, try corner-split to handle L-shaped roads
    ji = find_jump(pts)
    if ji < 0:
        return pts

    # Determine which side of the corner to filter
    if rx >= rz:
        # EW-primary: split after ji
        corner_block_x = int(round(pts[ji]['x'] - 0.5))
        last_ew_z = pts[ji]['z']
        # NS blocks: z >= corner z AND x >= corner x (skip fill behind corner)
        corner_z = int(round(pts[ji]['z'] - 0.5))
        ns_blocks = {(bx, bz) for bx, bz in ice_set if bz >= corner_z and bx >= corner_block_x}
        if not ns_blocks:
            return pts
        ns = [p for p in slice_by_z(ns_blocks) if p['z'] > last_ew_z]
        result = pts[:ji + 1] + ns
    else:
        # NS-primary: split after ji
        corner_block_z = int(round(pts[ji]['z'] - 0.5))
        last_ns_x = pts[ji]['x']
        corner_x = int(round(pts[ji]['x'] - 0.5))
        ew_blocks = {(bx, bz) for bx, bz in ice_set if bx >= corner_x and bz >= corner_block_z}
        if not ew_blocks:
            return pts
        ew = [p for p in slice_by_x(ew_blocks) if p['x'] > last_ns_x]
        result = pts[:ji + 1] + ew

    # Validate: the corner-split must actually cover the start area.  A
    # multi-lobe ice sheet (several parallel strips with gaps) shows up as a
    # big x/z jump between adjacent slices, but it is NOT an L-shaped road —
    # corner-split then chops off the lobe containing the boat, so the
    # centerline misses the start entirely and trajectory[0] lands tens of
    # blocks away → instant "deviated".  Fall back to the full slice in that
    # case; the caller's start-trim will anchor the path to the boat's lobe.
    near = min((dist(p, start) for p in result), default=1e18)
    if near > 8.0:
        return pts
    return result


# ── Step 3: B-Spline Fitting ──────────────────────────────────────────

def fit_bspline(points, samples_per_segment=4):
    """
    Fit cubic Catmull-Rom spline through waypoints.
    Equivalent to B-spline with uniform knots for this use case.
    """
    n = len(points)
    if n < 2:
        return points

    trajectory = []
    for i in range(n - 1):
        p0 = points[max(0, i - 1)]
        p1 = points[i]
        p2 = points[i + 1]
        p3 = points[min(n - 1, i + 2)]

        x0, z0 = p0['x'], p0['z']
        x1, z1 = p1['x'], p1['z']
        x2, z2 = p2['x'], p2['z']
        x3, z3 = p3['x'], p3['z']

        for s in range(samples_per_segment):
            t = s / samples_per_segment
            t2 = t * t
            t3 = t2 * t

            x = 0.5 * (
                (-x0 + 3 * x1 - 3 * x2 + x3) * t3 +
                (2 * x0 - 5 * x1 + 4 * x2 - x3) * t2 +
                (-x0 + x2) * t + 2 * x1
            )
            z = 0.5 * (
                (-z0 + 3 * z1 - 3 * z2 + z3) * t3 +
                (2 * z0 - 5 * z1 + 4 * z2 - z3) * t2 +
                (-z0 + z2) * t + 2 * z1
            )
            trajectory.append({'x': x, 'z': z})

    # Add last point
    trajectory.append({'x': points[-1]['x'], 'z': points[-1]['z']})
    return trajectory


# ── Step 4: Compute Yaw + Curvature ───────────────────────────────────

def compute_yaw_and_curvature(trajectory):
    """
    Compute yaw (heading direction) and curvature at each trajectory point
    using first and second derivatives of the Catmull-Rom parametrics.
    """
    n = len(trajectory)
    if n < 3:
        for p in trajectory:
            p['yaw'] = 0
            p['curvature'] = 0
        return trajectory

    for i in range(n):
        if i == 0:
            dx = trajectory[1]['x'] - trajectory[0]['x']
            dz = trajectory[1]['z'] - trajectory[0]['z']
            yaw = math.degrees(math.atan2(-dx, dz))
            trajectory[i]['yaw'] = yaw
            trajectory[i]['curvature'] = 0
        elif i == n - 1:
            dx = trajectory[n - 1]['x'] - trajectory[n - 2]['x']
            dz = trajectory[n - 1]['z'] - trajectory[n - 2]['z']
            yaw = math.degrees(math.atan2(-dx, dz))
            trajectory[i]['yaw'] = yaw
            trajectory[i]['curvature'] = 0
        else:
            # First derivative (tangent)
            dx = (trajectory[i + 1]['x'] - trajectory[i - 1]['x']) / 2
            dz = (trajectory[i + 1]['z'] - trajectory[i - 1]['z']) / 2
            yaw = math.degrees(math.atan2(-dx, dz))
            trajectory[i]['yaw'] = yaw

            # Curvature κ = |x'z'' - z'x''| / (x'² + z'²)^(3/2)
            x_prev, z_prev = trajectory[i - 1]['x'], trajectory[i - 1]['z']
            x_cur, z_cur = trajectory[i]['x'], trajectory[i]['z']
            x_next, z_next = trajectory[i + 1]['x'], trajectory[i + 1]['z']

            x1, z1 = x_cur - x_prev, z_cur - z_prev
            x2, z2 = x_next - x_cur, z_next - z_cur
            l1 = math.sqrt(x1 * x1 + z1 * z1)
            l2 = math.sqrt(x2 * x2 + z2 * z2)

            if l1 > 0.001 and l2 > 0.001:
                dot_val = (x1 / l1) * (x2 / l2) + (z1 / l1) * (z2 / l2)
                dot_val = max(-1, min(1, dot_val))
                angle = math.acos(dot_val)
                seg_len = (l1 + l2) / 2
                trajectory[i]['curvature'] = angle / max(seg_len, 0.5)
            else:
                trajectory[i]['curvature'] = 0

    return trajectory


# ── Step 5: Action Baking ─────────────────────────────────────────────

def bake_actions(centerline, drag):
    """
    Path-following action baking.

    Simulates the boat advancing along the centerline at its true
    acceleration profile and issues LEFT/RIGHT/STRAIGHT so the boat's
    yaw chases the direction of the path a LOOKAHEAD distance ahead of
    its current arc position.  This works at ANY speed: during the
    acceleration phase the boat travels fewer blocks per tick, so it has
    more ticks to complete the same per-block turn — a start→end linear
    ramp (whether keyed to ticks or to distance) instead over/under-turns
    because the turn must fit in whatever ticks the boat actually spends
    on that path length.

    Consecutive same actions are compressed into keyframes.
    """
    if len(centerline) < 2:
        return [], 0

    # Smooth the staircase centerline (slice-median z quantizes by 0.5-2.0
    # per column, so raw segment directions flip wildly between -90° and
    # -63°).  A 5-point window average removes the stair-step noise before
    # computing headings; without it a path-follower zigzags chasing the
    # noise instead of the road.  Main already smoothed the same window, so
    # this second pass is idempotent.  Skip <5 points: a 2-point fallback
    # line has no noise and would collapse to its midpoint.
    K = 2
    if len(centerline) >= 5:
        smoothed = []
        for i in range(len(centerline)):
            lo = max(0, i - K)
            hi = min(len(centerline) - 1, i + K)
            pts = centerline[lo:hi + 1]
            smoothed.append({
                'x': sum(p['x'] for p in pts) / len(pts),
                'z': sum(p['z'] for p in pts) / len(pts),
            })
        centerline = smoothed

    # Segment directions (path-local yaw at each segment)
    seg_yaw = []
    for i in range(len(centerline) - 1):
        dx = centerline[i + 1]['x'] - centerline[i]['x']
        dz = centerline[i + 1]['z'] - centerline[i]['z']
        seg_yaw.append(math.degrees(math.atan2(-dx, dz)))

    # Cumulative arc length
    cum = [0.0]
    for i in range(len(centerline) - 1):
        cum.append(cum[-1] + math.hypot(
            centerline[i + 1]['x'] - centerline[i]['x'],
            centerline[i + 1]['z'] - centerline[i]['z']))
    total_path = cum[-1]
    if total_path < 1e-6:
        return [], 0

    # Velocity profile (blocks/tick from standstill)
    d_per_tick = []
    v = 0.0
    while True:
        v = v * drag + FORWARD_FORCE
        d_per_tick.append(v)
        if sum(d_per_tick) >= total_path and len(d_per_tick) >= 5:
            break
        if len(d_per_tick) > 10000:
            break
    total_ticks = len(d_per_tick)

    LOOKAHEAD = 3.0  # blocks ahead along path
    HYS = 1.0        # degrees of error needed before correcting

    def point_at(arc):
        """Interpolated centerline point at arc length `arc`."""
        a = min(arc, total_path)
        seg = 0
        while seg < len(cum) - 2 and cum[seg + 1] < a:
            seg += 1
        t = (a - cum[seg]) / (cum[seg + 1] - cum[seg] + 1e-9)
        return (centerline[seg]['x'] + t * (centerline[seg + 1]['x'] - centerline[seg]['x']),
                centerline[seg]['z'] + t * (centerline[seg + 1]['z'] - centerline[seg]['z']))

    def heading_at_arc(arc):
        """Direction of the chord between arc and arc+LOOKAHEAD (averages
        out the residual 0.5-block stair-step noise in the centerline)."""
        x1, z1 = point_at(arc)
        x2, z2 = point_at(arc + LOOKAHEAD)
        return math.degrees(math.atan2(-(x2 - x1), z2 - z1))

    def project_arc(x, z):
        """Arc length of the closest point on the centerline to (x, z)."""
        best = None
        best_dist = 1e18
        for i in range(len(centerline) - 1):
            ax, az = centerline[i]['x'], centerline[i]['z']
            bx, bz = centerline[i + 1]['x'], centerline[i + 1]['z']
            abx, abz = bx - ax, bz - az
            t = ((x - ax) * abx + (z - az) * abz) / (abx * abx + abz * abz + 1e-9)
            t = max(0.0, min(1.0, t))
            d = math.hypot(x - (ax + t * abx), z - (az + t * abz))
            if d < best_dist:
                best_dist = d
                best = cum[i] + t * (cum[i + 1] - cum[i])
        return best if best is not None else 0.0

    actions = []
    prev_action = None
    start_tick = 0
    px, pz = centerline[0]['x'], centerline[0]['z']
    yaw = heading_at_arc(0.0)
    for tick in range(total_ticks):
        # True-boat simulation: yaw drives displacement, then project the
        # real position back onto the path to pick the lookahead heading.
        arc = project_arc(px, pz)
        target_yaw = heading_at_arc(arc)
        err = normalize_angle(target_yaw - yaw)
        if err > HYS:
            act = "RIGHT"
            yaw += 1.0
        elif err < -HYS:
            act = "LEFT"
            yaw -= 1.0
        else:
            act = "STRAIGHT"

        if act != prev_action:
            if prev_action is not None:
                actions.append({'tick': start_tick, 'action': prev_action,
                                'duration': tick - start_tick})
            prev_action = act
            start_tick = tick

        speed = d_per_tick[tick]
        yr = math.radians(yaw)
        px += speed * (-math.sin(yr))
        pz += speed * math.cos(yr)

    if prev_action is not None:
        actions.append({'tick': start_tick, 'action': prev_action,
                        'duration': total_ticks - start_tick})

    return [a for a in actions if a['action'] != 'STRAIGHT'], total_ticks


# ── Main ──────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) > 1:
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            raw = f.read()
    else:
        raw = sys.stdin.read()
        if raw.startswith('\ufeff'):
            raw = raw[1:]
    data = json.loads(raw)

    ice_blocks = data.get('ice_blocks', [])
    start = data['start']
    target = data['target']
    ice_y = data.get('ice_y', int(start.get('y', 64)) - 1)
    drag = data.get('drag', 0.98)
    loaded_range = data.get('loaded_chunk_range', None)

    if not loaded_range:
        # Default: use a big range
        loaded_range = {
            'min_x': -512, 'max_x': 512,
            'min_z': -512, 'max_z': 512,
        }

    # 1. Temp target
    temp = compute_temp_target(start, target, loaded_range)
    temp_target = {'x': temp[0], 'z': temp[1]}

    # 2. Centerline
    centerline = extract_centerline(ice_blocks, start, temp_target, ice_y)
    if len(centerline) < 5:
        samples = 20
        sx, sz = start['x'], start['z']
        tx, tz = temp_target['x'], temp_target['z']
        centerline = [
            {'x': sx + (tx - sx) * i / (samples - 1),
             'z': sz + (tz - sz) * i / (samples - 1)}
            for i in range(samples)
        ]
    else:
        # Trim so the path starts at the road point nearest the boat.
        # Without this, trajectory[0] sits at one END of the scanned road,
        # so a boat starting mid-road gets start_yaw pointing along the
        # wrong direction AND its t=0 position is far from trajectory[0]
        # → spurious "deviated" stop before the boat even moves.
        start_idx = min(range(len(centerline)), key=lambda i: dist(centerline[i], start))
        if start_idx == 0:
            pass  # already at the start end
        elif start_idx == len(centerline) - 1:
            # Boat is at the far end — reverse so we head back toward target
            centerline = list(reversed(centerline))
        else:
            # Mid-road: keep the sub-sequence pointing TOWARD the target
            toward_next = dist(centerline[start_idx + 1], target)
            toward_prev = dist(centerline[start_idx - 1], target)
            if toward_prev < toward_next:
                centerline = list(reversed(centerline[:start_idx + 1]))
            else:
                centerline = centerline[start_idx:]
        if len(centerline) < 2:
            centerline = [
                {'x': start['x'], 'z': start['z']},
                {'x': temp_target['x'], 'z': temp_target['z']},
            ]

    # Truncate the path at the point nearest the (user) target so the boat
    # stops at the goal's ice instead of riding the centerline all the way
    # to the far end of the scanned sheet (which can be 50+ blocks past the
    # target on a multi-lobe ice sheet).
    end_idx = min(range(len(centerline)), key=lambda i: dist(centerline[i], target))
    centerline = centerline[:end_idx + 1]
    if len(centerline) < 2:
        centerline = [
            {'x': start['x'], 'z': start['z']},
            {'x': temp_target['x'], 'z': temp_target['z']},
        ]

    # Use centerline endpoint as actual navigation target
    temp_target = {'x': centerline[-1]['x'], 'z': centerline[-1]['z']}

    # Smooth the staircase centerline ONCE, before BOTH the B-spline (which
    # feeds Java's setYaw + deviation check) and bake_actions.  Previously
    # only bake_actions smoothed internally, so the Java trajectory carried
    # raw staircase segment directions (flipping between -90° and -63°)
    # while bake_actions simulated from the smoothed chord — a start-heading
    # mismatch of ~5.7° (up to ~27° where the stair-step is worst) → the
    # boat's real heading never matched what the action baking assumed.
    # Skip smoothing for <5 points: a 2-point fallback line has no stair-step
    # noise and a 5-point window would collapse it to its midpoint.
    K = 2
    if len(centerline) >= 5:
        smoothed = []
        for i in range(len(centerline)):
            lo = max(0, i - K)
            hi = min(len(centerline) - 1, i + K)
            pts = centerline[lo:hi + 1]
            smoothed.append({
                'x': sum(p['x'] for p in pts) / len(pts),
                'z': sum(p['z'] for p in pts) / len(pts),
            })
        centerline = smoothed

    # 3. B-spline
    trajectory = fit_bspline(centerline, samples_per_segment=4)

    # 4. Yaw + curvature
    trajectory = compute_yaw_and_curvature(trajectory)

    # 5. Action baking (using centerline, not B-spline trajectory)
    actions, total_ticks = bake_actions(centerline, drag)

    # Output
    output = {
        'trajectory': [
            {
                'x': round(p['x'], 2),
                'z': round(p['z'], 2),
                'yaw': round(p.get('yaw', 0), 1),
                'curvature': round(p.get('curvature', 0), 4),
            }
            for p in trajectory
        ],
        'actions': actions,
        'temp_target': {
            'x': round(temp_target['x'], 1),
            'z': round(temp_target['z'], 1),
        },
        'ice_y': ice_y,
        'drag': drag,
        'total_ticks': total_ticks,
        'centerline_points': len(centerline),
        'trajectory_points': len(trajectory),
        'action_keyframes': len(actions),
        'debug_start_dist': round(dist(centerline[0], start), 1) if centerline else -1,
        'debug_centerline_first': {'x': round(centerline[0]['x'], 1), 'z': round(centerline[0]['z'], 1)} if centerline else None,
        'debug_centerline_last': {'x': round(centerline[-1]['x'], 1), 'z': round(centerline[-1]['z'], 1)} if centerline else None,
    }

    print(json.dumps(output))


if __name__ == '__main__':
    main()
