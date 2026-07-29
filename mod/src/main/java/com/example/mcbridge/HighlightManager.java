package com.example.mcbridge;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HighlightManager {
    public record HighlightPos(BlockPos pos, float r, float g, float b, float a, long endTime) {}
    private static final CopyOnWriteArrayList<HighlightPos> blocks = new CopyOnWriteArrayList<>();

    public static void addBlock(BlockPos pos, float r, float g, float b, float a, long durationMs) {
        long end = durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
        blocks.add(new HighlightPos(pos, r, g, b, a, end));
    }

    public static void clear() { blocks.clear(); }

    public static List<HighlightPos> getActive() {
        long now = System.currentTimeMillis();
        List<HighlightPos> active = new ArrayList<>();
        for (HighlightPos h : blocks) {
            if (now < h.endTime) active.add(h);
        }
        return active;
    }

    public static void removeExpired() {
        long now = System.currentTimeMillis();
        blocks.removeIf(h -> now >= h.endTime);
    }

    public static int count() { return blocks.size(); }
}
