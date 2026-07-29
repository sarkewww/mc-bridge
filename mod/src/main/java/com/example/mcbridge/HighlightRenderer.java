package com.example.mcbridge;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

public class HighlightRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var highlights = HighlightManager.getActive();
            if (highlights.isEmpty()) return;

            VertexConsumerProvider vcp = context.consumers();
            VertexConsumer consumer = vcp.getBuffer(RenderLayer.getLines());
            Camera camera = context.camera();
            MatrixStack ms = context.matrixStack();

            ms.push();
            ms.translate(-camera.getPos().x, -camera.getPos().y, -camera.getPos().z);

            for (var h : highlights) {
                drawBox(ms, consumer, h.pos(), h.r(), h.g(), h.b(), h.a());
            }

            ms.pop();
        });

        new java.util.Timer(true).scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() { HighlightManager.removeExpired(); }
        }, 5000, 5000);
    }

    private static void drawBox(MatrixStack ms, VertexConsumer consumer, BlockPos pos, float r, float g, float b, float a) {
        float x = pos.getX(), y = pos.getY(), z = pos.getZ();
        float x2 = x + 1, y2 = y + 1, z2 = z + 1;

        // Bottom face
        vertex(consumer, ms, x, y, z, r, g, b, a);
        vertex(consumer, ms, x2, y, z, r, g, b, a);
        vertex(consumer, ms, x2, y, z, r, g, b, a);
        vertex(consumer, ms, x2, y, z2, r, g, b, a);
        vertex(consumer, ms, x2, y, z2, r, g, b, a);
        vertex(consumer, ms, x, y, z2, r, g, b, a);
        vertex(consumer, ms, x, y, z2, r, g, b, a);
        vertex(consumer, ms, x, y, z, r, g, b, a);
        // Top face
        vertex(consumer, ms, x, y2, z, r, g, b, a);
        vertex(consumer, ms, x2, y2, z, r, g, b, a);
        vertex(consumer, ms, x2, y2, z, r, g, b, a);
        vertex(consumer, ms, x2, y2, z2, r, g, b, a);
        vertex(consumer, ms, x2, y2, z2, r, g, b, a);
        vertex(consumer, ms, x, y2, z2, r, g, b, a);
        vertex(consumer, ms, x, y2, z2, r, g, b, a);
        vertex(consumer, ms, x, y2, z, r, g, b, a);
        // Vertical edges
        vertex(consumer, ms, x, y, z, r, g, b, a);
        vertex(consumer, ms, x, y2, z, r, g, b, a);
        vertex(consumer, ms, x2, y, z, r, g, b, a);
        vertex(consumer, ms, x2, y2, z, r, g, b, a);
        vertex(consumer, ms, x2, y, z2, r, g, b, a);
        vertex(consumer, ms, x2, y2, z2, r, g, b, a);
        vertex(consumer, ms, x, y, z2, r, g, b, a);
        vertex(consumer, ms, x, y2, z2, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack ms, float x, float y, float z, float r, float g, float b, float a) {
        consumer.vertex(ms.peek().getPositionMatrix(), x, y, z).color(r, g, b, a).normal(0, 0, 1);
    }
}
