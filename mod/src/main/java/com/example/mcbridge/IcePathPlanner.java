package com.example.mcbridge;

import java.util.*;

public class IcePathPlanner {

    static List<double[]> plan(IceRoadGraph graph, double startX, double startZ, double targetX, double targetZ) {
        long startNode = graph.nearestIce(startX, startZ);
        long targetNode = graph.nearestIce(targetX, targetZ);
        if (startNode < 0 || targetNode < 0) return Collections.emptyList();
        if (startNode == targetNode) return List.of(new double[]{
                IceRoadGraph.keyToX(targetNode) + 0.5, IceRoadGraph.keyToZ(targetNode) + 0.5});

        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Double> gScore = new HashMap<>();
        gScore.put(startNode, 0.0);

        PriorityQueue<Entry> open = new PriorityQueue<>();
        open.add(new Entry(startNode, heuristic(startNode, targetNode)));

        while (!open.isEmpty()) {
            Entry cur = open.poll();
            long curNode = cur.node;
            if (curNode == targetNode) return reconstruct(cameFrom, curNode, targetNode);

            for (long nb : graph.neighbors(curNode)) {
                double edgeWeight = graph.weight(nb);
                double tg = gScore.getOrDefault(curNode, Double.MAX_VALUE) + edgeWeight;
                if (tg < gScore.getOrDefault(nb, Double.MAX_VALUE)) {
                    cameFrom.put(nb, curNode);
                    gScore.put(nb, tg);
                    open.add(new Entry(nb, tg + heuristic(nb, targetNode)));
                }
            }
        }
        return Collections.emptyList();
    }

    private static double heuristic(long node, long target) {
        int x1 = IceRoadGraph.keyToX(node), z1 = IceRoadGraph.keyToZ(node);
        int x2 = IceRoadGraph.keyToX(target), z2 = IceRoadGraph.keyToZ(target);
        return Math.hypot(x2 - x1, z2 - z1) * 0.85;
    }

    private static List<double[]> reconstruct(Map<Long, Long> cameFrom, long current, long targetNode) {
        List<double[]> path = new ArrayList<>();
        long cur = current;
        while (cameFrom.containsKey(cur)) {
            path.add(new double[]{IceRoadGraph.keyToX(cur) + 0.5, IceRoadGraph.keyToZ(cur) + 0.5});
            cur = cameFrom.get(cur);
        }
        path.add(new double[]{IceRoadGraph.keyToX(cur) + 0.5, IceRoadGraph.keyToZ(cur) + 0.5});
        Collections.reverse(path);
        return simplifyPath(path);
    }

    private static List<double[]> simplifyPath(List<double[]> raw) {
        if (raw.size() <= 2) return raw;
        List<double[]> out = new ArrayList<>();
        out.add(raw.get(0));
        int last = 0;
        for (int i = 1; i < raw.size() - 1; i++) {
            double[] p0 = raw.get(last);
            double[] p1 = raw.get(i + 1);
            double[] pi = raw.get(i);
            double dev = pointToSegmentDist(pi[0], pi[1], p0[0], p0[1], p1[0], p1[1]);
            if (dev > 0.3) {
                out.add(pi);
                last = i;
            }
        }
        out.add(raw.get(raw.size() - 1));
        return out;
    }

    private static double pointToSegmentDist(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double lenSq = dx * dx + dz * dz;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / lenSq));
        double nx = ax + dx * t, nz = az + dz * t;
        return Math.hypot(px - nx, pz - nz);
    }

    private static class Entry implements Comparable<Entry> {
        long node;
        double f;
        Entry(long n, double f) { this.node = n; this.f = f; }
        public int compareTo(Entry o) { return Double.compare(f, o.f); }
    }
}
