package com.example.mcbridge;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

public class IceRoadGraph {

    static final double BLUE_ICE_WEIGHT = 0.85;
    static final double PACKED_ICE_WEIGHT = 1.0;
    static final double ICE_WEIGHT = 1.05;

    private final Map<Long, Double> weights = new HashMap<>();
    private final Map<Long, List<Long>> adjacency = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private int scanRadius = 8;

    void setScanRadius(int r) { scanRadius = Math.min(Math.max(r, 2), 8); }
    int getScanRadius() { return scanRadius; }

    void scanFrom(double px, double py, double pz) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;
        int cx = (int)Math.floor(px / 16.0);
        int cz = (int)Math.floor(pz / 16.0);
        for (int dcx = -scanRadius; dcx <= scanRadius; dcx++) {
            for (int dcz = -scanRadius; dcz <= scanRadius; dcz++) {
                ChunkPos cp = new ChunkPos(cx + dcx, cz + dcz);
                scanChunk(client, cp, py);
            }
        }
    }

    boolean containsNode(long key) {
        return weights.containsKey(key);
    }

    private void scanChunk(MinecraftClient client, ChunkPos cp, double playerY) {
        var world = client.world;
        if (world == null) return;
        if (scannedChunks.contains(cp)) return;
        if (!world.isChunkLoaded(cp.x, cp.z)) return;
        WorldChunk chunk = world.getChunk(cp.x, cp.z);
        if (chunk == null) return;
        scannedChunks.add(cp);
        int py = (int)Math.floor(playerY);
        int minY = Math.max(world.getBottomY(), py - 32);
        int maxY = Math.min(world.getTopY(), py + 32);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(cp.x * 16 + lx, y, cp.z * 16 + lz);
                    BlockState state = world.getBlockState(pos);
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    double w = iceWeight(blockId);
                    if (w < 0) continue;
                    long key = posToKey(pos.getX(), pos.getZ());
                    if (weights.containsKey(key) && weights.get(key) <= w) continue;
                    weights.put(key, w);
                    adjacency.putIfAbsent(key, new ArrayList<>());
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        long nk = posToKey(pos.getX() + d[0], pos.getZ() + d[1]);
                        double nw = iceWeightAt(world, pos.getX() + d[0], pos.getY(), pos.getZ() + d[1]);
                        if (nw >= 0) {
                            weights.putIfAbsent(nk, nw);
                            adjacency.putIfAbsent(nk, new ArrayList<>());
                            adjacency.get(key).add(nk);
                        }
                    }
                }
            }
        }
    }

    private double iceWeightAt(net.minecraft.world.World world, int x, int y, int z) {
        BlockState state = world.getBlockState(new BlockPos(x, y, z));
        return iceWeight(Registries.BLOCK.getId(state.getBlock()).toString());
    }

    private double iceWeight(String blockId) {
        if (blockId.contains("blue_ice")) return BLUE_ICE_WEIGHT;
        if (blockId.contains("packed_ice")) return PACKED_ICE_WEIGHT;
        if (blockId.equals("minecraft:ice")) return ICE_WEIGHT;
        return -1;
    }

    List<Long> neighbors(long key) {
        return adjacency.getOrDefault(key, Collections.emptyList());
    }

    double weight(long key) {
        return weights.getOrDefault(key, ICE_WEIGHT);
    }

    boolean hasNode(long key) {
        return weights.containsKey(key);
    }

    long nearestIce(double x, double z) {
        long best = -1L;
        double bestDist = Double.MAX_VALUE;
        int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                long key = posToKey(bx + dx, bz + dz);
                if (!weights.containsKey(key)) continue;
                double cx = (bx + dx) + 0.5, cz = (bz + dz) + 0.5;
                double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                if (d < bestDist) { bestDist = d; best = key; }
            }
        }
        return best;
    }

    static long posToKey(int x, int z) {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }

    static int keyToX(long key) { return (int)(key >> 32); }
    static int keyToZ(long key) { return (int)key; }

    Map<Long, Double> getWeights() { return Collections.unmodifiableMap(weights); }
    int nodeCount() { return weights.size(); }

    void clear() {
        weights.clear();
        adjacency.clear();
        scannedChunks.clear();
    }
}
