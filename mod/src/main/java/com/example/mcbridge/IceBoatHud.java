package com.example.mcbridge;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import java.util.*;

public class IceBoatHud {

    private static boolean registered = false;

    static void register() {
        if (registered) return;
        registered = true;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!IceController.isRunning()) return;
            var graph = IceController.getGraph();
            var waypoints = IceController.getWaypoints();
            if (graph == null && (waypoints == null || waypoints.isEmpty())) return;

            VertexConsumerProvider vcp = context.consumers();
            VertexConsumer consumer = vcp.getBuffer(RenderLayer.getLines());
            Camera camera = context.camera();
            MatrixStack ms = context.matrixStack();

            ms.push();
            ms.translate(-camera.getPos().x, -camera.getPos().y, -camera.getPos().z);
            var mat = ms.peek().getPositionMatrix();

            var client = MinecraftClient.getInstance();
            if (client.player == null) { ms.pop(); return; }
            double py = client.player.getY();

            // Road graph (blue - faint)
            if (graph != null) {
                int shown = 0;
                for (var entry : graph.getWeights().entrySet()) {
                    long key = entry.getKey();
                    int x = IceRoadGraph.keyToX(key);
                    int z = IceRoadGraph.keyToZ(key);
                    for (long nb : graph.neighbors(key)) {
                        int nx = IceRoadGraph.keyToX(nb);
                        int nz = IceRoadGraph.keyToZ(nb);
                        if (key < nb) {
                            line(consumer, mat, x + 0.5f, (float)py, z + 0.5f, nx + 0.5f, (float)py, nz + 0.5f,
                                    0.3f, 0.5f, 1.0f, 0.2f);
                            shown++;
                        }
                    }
                    if (shown > 4000) break;
                }
            }

            // Planned path (green)
            if (waypoints != null && waypoints.size() > 1) {
                for (int i = 0; i < waypoints.size() - 1; i++) {
                    double[] p0 = waypoints.get(i), p1 = waypoints.get(i + 1);
                    line(consumer, mat, (float)p0[0], (float)py + 0.1f, (float)p0[1],
                            (float)p1[0], (float)py + 0.1f, (float)p1[1], 0.2f, 1.0f, 0.2f, 0.9f);
                }
                for (int i = 0; i < waypoints.size(); i++) {
                    double[] wp = waypoints.get(i);
                    float b = i == IceController.getWpIndex() ? 1.0f : 0.5f;
                    markerX(consumer, mat, (float)wp[0], (float)py + 0.05f, (float)wp[1],
                            0.8f, 1.0f, 0.3f, b);
                }
            }

            // Prediction trajectory arc (yellow)
            var pred = IceController.getLastPrediction();
            if (pred != null && pred.size() > 1) {
                for (int i = 0; i < pred.size() - 1; i++) {
                    double[] pa = pred.get(i), pb = pred.get(i + 1);
                    float alpha = 0.3f + 0.6f * i / (pred.size() - 1);
                    line(consumer, mat, (float)pa[0], (float)py + 0.15f, (float)pa[1],
                            (float)pb[0], (float)py + 0.15f, (float)pb[1], 1.0f, 0.8f, 0.1f, alpha);
                }
            }

            // Target beam (red)
            if (waypoints != null && !waypoints.isEmpty()) {
                double[] last = waypoints.get(waypoints.size() - 1);
                double tx = last[0], tz = last[1];
                for (int yy = -5; yy <= 5; yy++) {
                    line(consumer, mat, (float)tx - 0.3f, (float)py + yy, (float)tz - 0.3f,
                            (float)tx + 0.3f, (float)py + yy, (float)tz + 0.3f, 1.0f, 0.2f, 0.2f, 0.8f);
                }
            }

            ms.pop();
        });
    }

    private static void line(VertexConsumer vc, org.joml.Matrix4f mat,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }

    private static void markerX(VertexConsumer vc, org.joml.Matrix4f mat,
                                 float x, float y, float z, float r, float g, float b, float a) {
        float s = 0.25f;
        line(vc, mat, x - s, y, z - s, x + s, y, z + s, r, g, b, a);
        line(vc, mat, x - s, y, z + s, x + s, y, z - s, r, g, b, a);
    }
}
