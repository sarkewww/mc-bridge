package com.example.mcbridge;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoExploreService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean running = false;
    private static Thread thread = null;

    private static int startChunkX, startChunkZ;
    private static int currentRing = 0;
    private static int currentStep = 0;
    private static int maxRadius = 100;
    private static String mode = "spiral";
    private static final Set<String> visitedChunks = ConcurrentHashMap.newKeySet();
    private static final List<Discovery> discoveries = new CopyOnWriteArrayList<>();

    public static class Discovery {
        String time;
        int cx, cz;
        String biome;
        String notableBlocks;
        String dimension;
    }

    public static synchronized String start(int radius, String exploreMode) {
        if (running) throw new RuntimeException("Already exploring");
        var client = MinecraftClient.getInstance();
        if (client.player == null) throw new RuntimeException("Not in game");
        if (client.world == null) throw new RuntimeException("Not in a world");

        startChunkX = Math.floorDiv(client.player.getBlockX(), 16);
        startChunkZ = Math.floorDiv(client.player.getBlockZ(), 16);
        maxRadius = Math.min(radius, 500);
        mode = exploreMode != null ? exploreMode : "spiral";
        currentRing = 0;
        currentStep = 0;
        visitedChunks.clear();
        discoveries.clear();
        running = true;

        thread = new Thread(() -> runLoop(), "AutoExploreThread");
        thread.setDaemon(true);
        thread.start();

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("message", "Exploration started at chunk [" + startChunkX + ", " + startChunkZ + "] radius " + maxRadius);
        return r.toString();
    }

    public static synchronized String stop() {
        if (!running) throw new RuntimeException("Not exploring");
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        save();
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("message", "Exploration stopped");
        return r.toString();
    }

    public static synchronized String status() {
        JsonObject r = new JsonObject();
        r.addProperty("running", running);
        r.addProperty("mode", mode);
        r.addProperty("maxRadius", maxRadius);
        r.addProperty("chunksVisited", visitedChunks.size());
        r.addProperty("discoveries", discoveries.size());
        r.addProperty("currentRing", currentRing);
        if (running) {
            r.addProperty("currentChunk", (startChunkX + getOffsetX()) + ", " + (startChunkZ + getOffsetZ()));
        }
        JsonArray biomes = new JsonArray();
        discoveries.stream().map(d -> d.biome).distinct().forEach(b -> biomes.add(b));
        r.add("biomesFound", biomes);
        return r.toString();
    }

    private static void runLoop() {
        while (running) {
            try {
                int dx = getOffsetX();
                int dz = getOffsetZ();
                int targetCx = startChunkX + dx;
                int targetCz = startChunkZ + dz;

                String chunkKey = targetCx + "," + targetCz;
                if (!visitedChunks.contains(chunkKey)) {
                    visitedChunks.add(chunkKey);
                    scanCurrentChunk(targetCx, targetCz);
                }

                advanceStep();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                McBridgeMod.LOGGER.error("[mc-bridge] AutoExplore error: {}", e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
            }
        }
        releaseKeys();
    }

    private static void scanCurrentChunk(int cx, int cz) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;

        int blockX = cx * 16 + 8;
        int blockZ = cz * 16 + 8;
        walkToward(blockX, blockZ);

        String[] data = new String[3];
        client.execute(() -> {
            if (client.world != null && client.player != null) {
                try {
                    var pos = client.world.getBiome(client.player.getBlockPos());
                    var id = pos.getKey().orElse(null);
                    data[0] = id != null ? id.getValue().toString() : "unknown";
                    data[1] = client.world.getRegistryKey().getValue().toString();
                    data[2] = detectPOI(client, cx, cz);
                } catch (Exception e) {
                    data[0] = "error:" + e.getMessage();
                }
            }
        });

        String biome = data[0] != null ? data[0] : "unknown";
        String dim = data[1] != null ? data[1] : "unknown";
        String poi = data[2] != null ? data[2] : "";

        Discovery d = new Discovery();
        d.time = Instant.now().toString();
        d.cx = cx;
        d.cz = cz;
        d.biome = biome;
        d.dimension = dim;
        d.notableBlocks = poi;

        discoveries.add(d);
        if (!poi.isEmpty()) {
            MemoryService.addMemory(
                "Found " + poi + " at chunk [" + cx + "," + cz + "] in " + biome,
                "observation", 2, "exploration,poi," + poi.replace(" ", "_")
            );
        }
        McBridgeMod.LOGGER.debug("[mc-bridge] Explored chunk [{},{}] biome={} poi={}", cx, cz, biome, poi);
    }

    private static String detectPOI(MinecraftClient client, int cx, int cz) {
        var world = client.world;
        if (world == null) return "";
        int baseX = cx * 16;
        int baseZ = cz * 16;
        int topY = world.getTopY();
        int bottomY = world.getBottomY();
        boolean hasBed = false, hasWorkbench = false, hasAltar = false;
        boolean hasMossy = false, hasObsidian = false, hasBookshelf = false;
        boolean hasAnvil = false, hasEnchanting = false, hasLodestone = false;

        for (int y = bottomY; y < topY && y < bottomY + 64; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    var state = world.getBlockState(new net.minecraft.util.math.BlockPos(baseX + x, y, baseZ + z));
                    if (state.isAir()) continue;
                    String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
                    if (id.contains("crafting_table")) hasWorkbench = true;
                    else if (id.contains("red_bed") || id.contains("white_bed")) hasBed = true;
                    else if (id.contains("enchanting_table")) hasEnchanting = true;
                    else if (id.contains("bookshelf")) hasBookshelf = true;
                    else if (id.contains("anvil")) hasAnvil = true;
                    else if (id.contains("lodestone")) hasLodestone = true;
                    else if (id.contains("mossy_cobblestone")) hasMossy = true;
                    else if (id.contains("obsidian") && !id.contains("crying")) hasObsidian = true;
                    else if (id.contains("chiseled_")) hasAltar = true;
                }
            }
        }

        int score = (hasWorkbench ? 1 : 0) + (hasBed ? 2 : 0) + (hasAltar ? 2 : 0)
            + (hasMossy ? 1 : 0) + (hasObsidian ? 2 : 0) + (hasBookshelf ? 1 : 0)
            + (hasAnvil ? 1 : 0) + (hasEnchanting ? 2 : 0) + (hasLodestone ? 2 : 0);

        if (score >= 4) {
            if (hasWorkbench && hasBed) return "village";
            if (hasAltar && hasMossy) return "jungle_temple";
            if (hasObsidian && hasAltar) return "desert_temple";
            if (hasEnchanting && hasBookshelf) return "stronghold_library";
            if (hasObsidian && !hasAltar && !hasWorkbench) return "ruined_portal";
            if (hasAnvil && hasLodestone) return "ancient_city_debris";
            return "interesting_structures";
        }
        return "";
    }

    private static void walkToward(int targetX, int targetZ) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;

        try {
            int attempts = 0;
            while (running && attempts < 60) {
                boolean[] arrived = {false};
                client.execute(() -> {
                    if (client.player != null) {
                        double dx = targetX - client.player.getX();
                        double dz = targetZ - client.player.getZ();
                        double d = Math.sqrt(dx * dx + dz * dz);
                        arrived[0] = d < 3;
                        if (!arrived[0]) {
                            client.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                            client.player.setPitch(0);
                            client.options.forwardKey.setPressed(true);
                        }
                    }
                });
                if (arrived[0]) break;
                Thread.sleep(500);
                attempts++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            client.execute(() -> {
                if (client.player != null) {
                    client.options.forwardKey.setPressed(false);
                }
            });
        }
    }

    private static void releaseKeys() {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.options.forwardKey.setPressed(false);
            }
        });
    }

    private static void advanceStep() {
        currentStep++;
        int side = currentRing * 2 + 1;
        if (currentStep >= side * 4) {
            currentStep = 0;
            currentRing++;
            if (currentRing * 16 > maxRadius) {
                McBridgeMod.LOGGER.info("[mc-bridge] AutoExplore reached max radius {}. Stopping.", maxRadius);
                running = false;
                save();
            }
        }
    }

    private static int getOffsetX() {
        return spiralOffsetX(currentRing, currentStep);
    }

    private static int getOffsetZ() {
        return spiralOffsetZ(currentRing, currentStep);
    }

    private static int spiralOffsetX(int ring, int step) {
        int side = ring * 2 + 1;
        int posInSide = step % side;
        int sideIndex = step / side;
        return switch (sideIndex) {
            case 0 -> -ring + posInSide;
            case 1 -> ring;
            case 2 -> ring - posInSide;
            case 3 -> -ring;
            default -> 0;
        };
    }

    private static int spiralOffsetZ(int ring, int step) {
        int side = ring * 2 + 1;
        int posInSide = step % side;
        int sideIndex = step / side;
        return switch (sideIndex) {
            case 0 -> -ring;
            case 1 -> -ring + posInSide;
            case 2 -> ring;
            case 3 -> ring - posInSide;
            default -> 0;
        };
    }

    private static Path getDataPath() {
        var client = MinecraftClient.getInstance();
        String worldKey;
        if (client.world == null) {
            worldKey = "unknown";
        } else if (client.getCurrentServerEntry() != null) {
            worldKey = client.getCurrentServerEntry().address.replaceAll("[^a-zA-Z0-9]", "_");
        } else {
            worldKey = "local_" + client.world.getRegistryKey().getValue().getNamespace();
        }
        return Path.of(client.runDirectory.getPath(), "config", "mc-bridge-profiles", "exploration", worldKey + ".json");
    }

    private static synchronized void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("startChunkX", startChunkX);
            obj.addProperty("startChunkZ", startChunkZ);
            obj.addProperty("maxRadius", maxRadius);
            obj.addProperty("mode", mode);
            obj.addProperty("timestamp", Instant.now().toString());

            JsonArray visits = new JsonArray();
            for (String ck : visitedChunks) visits.add(ck);
            obj.add("visitedChunks", visits);

            JsonArray disc = new JsonArray();
            for (Discovery d : discoveries) disc.add(GSON.toJsonTree(d));
            obj.add("discoveries", disc);

            Path dir = getDataPath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Files.writeString(getDataPath(), GSON.toJson(obj));
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save exploration data: {}", e.getMessage());
        }
    }

    public static synchronized String getExplorationData() {
        try {
            if (Files.exists(getDataPath())) {
                return Files.readString(getDataPath());
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        throw new RuntimeException("No exploration data for this world");
    }
}
