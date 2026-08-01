package com.example.mcbridge;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IceController {

    static final int HORIZON = 20;
    static final double LOOKAHEAD = 8.0;
    static final double CTE_K = 0.35;
    static final double DEADBAND = 1.2;
    static final double ARRIVAL_THRESHOLD = 2.0;

    private static volatile boolean running = false;
    private static Thread thread = null;
    private static IceRoadGraph graph;
    private static IceRecorder recorder;

    private static volatile List<double[]> waypoints;
    private static volatile int wpIndex;
    private static double targetX, targetZ;
    private static volatile IcePhysicsEngine.Action lastAction = IcePhysicsEngine.Action.STRAIGHT;
    private static volatile IcePhysicsEngine.Action pendingAction = IcePhysicsEngine.Action.STRAIGHT;
    private static volatile Snapshot snapshot = null;
    private static volatile List<double[]> lastPrediction = null;

    static final class Snapshot {
        final double x, z, yaw, vx, vz;
        Snapshot(double x, double z, double yaw, double vx, double vz) {
            this.x = x; this.z = z; this.yaw = yaw; this.vx = vx; this.vz = vz;
        }
    }

    public static void onClientTick(MinecraftClient client) {
        if (!running) return;
        if (client.player == null) return;
        client.player.input.pressingForward = true;
        IcePhysicsEngine.Action a = pendingAction;
        client.player.input.pressingLeft = (a == IcePhysicsEngine.Action.LEFT);
        client.player.input.pressingRight = (a == IcePhysicsEngine.Action.RIGHT);
        snapshot = new Snapshot(client.player.getX(), client.player.getZ(), client.player.getYaw(),
                client.player.getVelocity().x, client.player.getVelocity().z);
    }

    public static synchronized String start(double tx, double tz, int scanRadius) {
        if (running) return error("Already navigating. Use stop first.");
        var client = MinecraftClient.getInstance();
        if (client.player == null) return error("Not in game");
        if (client.world == null) return error("Not in a world");

        targetX = tx;
        targetZ = tz;
        lastPrediction = null;
        pendingAction = IcePhysicsEngine.Action.STRAIGHT;

        graph = new IceRoadGraph();
        graph.setScanRadius(scanRadius > 0 ? scanRadius : 4);

        String scanErr = runScan(client);
        if (scanErr != null) return scanErr;

        try {
            long targetNode = graph.nearestIce(tx, tz);
            int retries = 0;
            while (targetNode < 0 && retries < 10) {
                Thread.sleep(300);
                scanErr = runScan(client);
                if (scanErr != null) return scanErr;
                targetNode = graph.nearestIce(tx, tz);
                retries++;
            }
            if (targetNode < 0) {
                return error("No ice found near target (" + (int) tx + ", " + (int) tz + "). Target chunk not loaded or no ice there.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Scan interrupted");
        }

        recorder = new IceRecorder();
        recorder.start(targetX, targetZ);

        double curX = client.player.getX();
        double curZ = client.player.getZ();
        waypoints = IcePathPlanner.plan(graph, curX, curZ, tx, tz);
        if (waypoints.isEmpty()) {
            recorder.stop("no_path");
            return error("No ice path found to target. Are there ice blocks connecting start to target?");
        }
        wpIndex = 0;

        running = true;
        thread = new Thread(IceController::controlLoop, "IceBoatController");
        thread.setDaemon(true);
        thread.start();

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("message", "Navigation started. Path: " + waypoints.size() + " waypoints.");
        r.addProperty("graph_nodes", graph.nodeCount());
        r.addProperty("scan_radius", graph.getScanRadius());
        r.addProperty("start", String.format("%.1f, %.1f", curX, curZ));
        r.addProperty("target", String.format("%.1f, %.1f", tx, tz));
        return r.toString();
    }

    private static String runScan(MinecraftClient client) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException[] err = new RuntimeException[1];
            client.execute(() -> {
                try {
                    graph.scanFrom(client.player.getX(), client.player.getY(), client.player.getZ());
                } catch (Exception e) {
                    err[0] = new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            if (!latch.await(10, TimeUnit.SECONDS)) throw new RuntimeException("Scan timed out");
            if (err[0] != null) throw err[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Scan interrupted");
        }
        return null;
    }

    public static synchronized String stop() {
        if (!running) return error("Not navigating.");
        stopInternal("manual");
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("message", "Navigation stopped.");
        return r.toString();
    }

    public static String status() {
        JsonObject r = new JsonObject();
        r.addProperty("running", running);
        if (!running) return r.toString();
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            r.addProperty("position", String.format("%.1f, %.1f", client.player.getX(), client.player.getZ()));
            double dist = Math.hypot(targetX - client.player.getX(), targetZ - client.player.getZ());
            r.addProperty("distance_to_target", String.format("%.1f", dist));
        }
        r.addProperty("target", String.format("%.1f, %.1f", targetX, targetZ));
        List<double[]> wp = waypoints;
        if (wp != null) {
            r.addProperty("waypoints", wp.size());
            r.addProperty("waypoint_index", wpIndex);
        }
        r.addProperty("last_action", lastAction.name());
        r.addProperty("graph_nodes", graph != null ? graph.nodeCount() : 0);
        return r.toString();
    }

    static List<double[]> getWaypoints() { return waypoints; }
    static int getWpIndex() { return wpIndex; }
    static boolean isRunning() { return running; }
    static IcePhysicsEngine.Action getLastAction() { return lastAction; }
    static IceRoadGraph getGraph() { return graph; }
    static IceRecorder getRecorder() { return recorder; }
    static List<double[]> getLastPrediction() { return lastPrediction; }

    private static void controlLoop() {
        try {
            while (running) {
                Snapshot snap = snapshot;
                if (snap == null) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                double px = snap.x;
                double pz = snap.z;
                double distToTarget = Math.hypot(targetX - px, targetZ - pz);
                if (distToTarget < ARRIVAL_THRESHOLD) {
                    stopInternal("arrived");
                    return;
                }

                List<double[]> wp = waypoints;
                int wpi = wpIndex;
                if (wpi >= wp.size()) {
                    replan(px, pz);
                    wp = waypoints;
                    wpi = wpIndex;
                    if (wp.isEmpty() || wpi >= wp.size()) {
                        stopInternal("replan_failed");
                        return;
                    }
                }

                double[] targetWp = wp.get(wpi);
                double wpDist = Math.hypot(targetWp[0] - px, targetWp[1] - pz);
                if (wpDist < 1.5 && wpi < wp.size() - 1) {
                    wpIndex = wpi + 1;
                }

                IcePhysicsEngine.State s = new IcePhysicsEngine.State(px, pz, snap.yaw, snap.vx, snap.vz);
                IcePhysicsEngine.Action best = greedyStep(s, wp, wpIndex);
                pendingAction = best;
                IcePhysicsEngine.Action act = best != null ? best : IcePhysicsEngine.Action.STRAIGHT;
                lastAction = act;

                if (best != null) {
                    lastPrediction = simulateTrace(s, best, 30);
                }

                if (recorder != null) recorder.record(px, pz, (float)snap.yaw, act, wpIndex, distToTarget);

                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] IceController error: {}", e.getMessage());
        } finally {
            stopInternal("loop_exit");
        }
    }

    private static IcePhysicsEngine.Action greedyStep(IcePhysicsEngine.State cur, List<double[]> path, int startIdx) {
        double bestCost = Double.MAX_VALUE;
        IcePhysicsEngine.Action bestAct = IcePhysicsEngine.Action.STRAIGHT;
        for (IcePhysicsEngine.Action a : IcePhysicsEngine.Action.values()) {
            IcePhysicsEngine.State st = cur.copy();
            IcePhysicsEngine.tick(st, a, false);
            double c = rolloutCost(st, path, startIdx, HORIZON - 1);
            if (c < bestCost) { bestCost = c; bestAct = a; }
        }
        return bestAct;
    }

    private static double rolloutCost(IcePhysicsEngine.State s, List<double[]> path, int startIdx, int remaining) {
        for (int i = 0; i < remaining; i++) {
            double ty = headingAlong(s, path, startIdx);
            double diff = IcePhysicsEngine.normAngle(ty - s.yaw);
            IcePhysicsEngine.Action a = diff > DEADBAND ? IcePhysicsEngine.Action.RIGHT
                    : (diff < -DEADBAND ? IcePhysicsEngine.Action.LEFT : IcePhysicsEngine.Action.STRAIGHT);
            IcePhysicsEngine.tick(s, a, false);
        }
        double[] wp = path.get(path.size() - 1);
        double[] npt = nearestPoint(path, s.x, s.z);
        double dev = Math.hypot(s.x - npt[0], s.z - npt[1]);
        double mis = Math.abs(IcePhysicsEngine.normAngle(headingAlong(s, path, startIdx) - s.yaw));
        double dtarget = Math.hypot(s.x - wp[0], s.z - wp[1]);
        return dev + 3.0 * mis + 0.1 * dtarget;
    }

    private static double headingAlong(IcePhysicsEngine.State s, List<double[]> path, int startIdx) {
        double[] nearest = nearestPoint(path, s.x, s.z);
        double arc = arcLengthToPoint(path, nearest[0], nearest[1], startIdx);
        double look = Math.min(arc + LOOKAHEAD, totalArcLength(path));
        double[] p2 = pointAtArc(path, look);
        double tangent = Math.toDegrees(Math.atan2(-(p2[0] - nearest[0]), p2[1] - nearest[1]));

        double segLen = Math.hypot(p2[0] - nearest[0], p2[1] - nearest[1]);
        if (segLen < 1e-9) return tangent;
        double nrmDx = -(p2[1] - nearest[1]) / segLen;
        double nrmDz = (p2[0] - nearest[0]) / segLen;
        double lat = (s.x - nearest[0]) * nrmDx + (s.z - nearest[1]) * nrmDz;
        double speed = Math.max(Math.hypot(s.vx, s.vz), 0.3);
        double corr = Math.toDegrees(Math.atan2(-CTE_K * lat, speed));
        return tangent + corr;
    }

    private static double[] nearestPoint(List<double[]> path, double x, double z) {
        double bestD = Double.MAX_VALUE;
        double bestX = 0, bestZ = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            double[] p0 = path.get(i), p1 = path.get(i + 1);
            double dx = p1[0] - p0[0], dz = p1[1] - p0[1];
            double segSq = dx * dx + dz * dz;
            double t = segSq == 0 ? 0 : Math.max(0, Math.min(1, ((x - p0[0]) * dx + (z - p0[1]) * dz) / segSq));
            double nx = p0[0] + dx * t, nz = p0[1] + dz * t;
            double d = (x - nx) * (x - nx) + (z - nz) * (z - nz);
            if (d < bestD) { bestD = d; bestX = nx; bestZ = nz; }
        }
        return new double[]{bestX, bestZ};
    }

    private static double arcLengthToPoint(List<double[]> path, double x, double z, int startIdx) {
        double arc = 0;
        for (int i = startIdx; i < path.size() - 1; i++) {
            double[] p0 = path.get(i), p1 = path.get(i + 1);
            double dx = p1[0] - p0[0], dz = p1[1] - p0[1];
            double segLen = Math.hypot(dx, dz);
            double segSq = dx * dx + dz * dz;
            double t = segSq == 0 ? 0 : Math.max(0, Math.min(1, ((x - p0[0]) * dx + (z - p0[1]) * dz) / segSq));
            if (t < 1.0) return arc + t * segLen;
            arc += segLen;
        }
        return arc;
    }

    private static double totalArcLength(List<double[]> path) {
        double arc = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            arc += Math.hypot(path.get(i + 1)[0] - path.get(i)[0], path.get(i + 1)[1] - path.get(i)[1]);
        }
        return arc;
    }

    private static double[] pointAtArc(List<double[]> path, double arc) {
        double cum = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            double[] p0 = path.get(i), p1 = path.get(i + 1);
            double seg = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
            if (arc <= cum + seg || i == path.size() - 2) {
                double t = seg == 0 ? 0 : Math.max(0, Math.min(1, (arc - cum) / seg));
                return new double[]{p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t};
            }
            cum += seg;
        }
        return path.get(path.size() - 1);
    }

    private static List<double[]> simulateTrace(IcePhysicsEngine.State s, IcePhysicsEngine.Action a, int ticks) {
        List<double[]> trace = new ArrayList<>();
        IcePhysicsEngine.State st = s.copy();
        for (int i = 0; i < ticks; i++) {
            IcePhysicsEngine.tick(st, a, false);
            trace.add(new double[]{st.x, st.z});
        }
        return trace;
    }

    private static boolean replan(double px, double pz) {
        if (graph == null) return false;
        List<double[]> newPath = IcePathPlanner.plan(graph, px, pz, targetX, targetZ);
        if (newPath.isEmpty()) return false;
        waypoints = newPath;
        wpIndex = 0;
        McBridgeMod.LOGGER.info("[mc-bridge] IceController replanned: {} waypoints", newPath.size());
        return true;
    }

    private static void stopInternal(String reason) {
        running = false;
        pendingAction = IcePhysicsEngine.Action.STRAIGHT;
        releaseKeys();
        if (recorder != null) recorder.stop(reason);
        McBridgeMod.LOGGER.info("[mc-bridge] IceController stopped: {}", reason);
    }

    private static void releaseKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.input.pressingForward = false;
                client.player.input.pressingLeft = false;
                client.player.input.pressingRight = false;
            }
        });
    }

    private static String error(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }
}
