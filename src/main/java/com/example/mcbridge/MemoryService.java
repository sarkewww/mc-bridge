package com.example.mcbridge;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MemoryService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<MemoryEntry> entries = new CopyOnWriteArrayList<>();

    public static class MemoryEntry {
        public String id;
        public String time;
        public String category; // event, location, player, observation, note
        public String content;
        public int importance; // 1-5
        public String tags; // comma-separated
        public String dimension;
        public double x, y, z; // associated position
    }

    private static Path getDataPath() {
        var client = MinecraftClient.getInstance();
        return Path.of(client.runDirectory.getPath(), "config", "mc-bridge-profiles", "memories.json");
    }

    static {
        load();
    }

    private static synchronized void load() {
        Path path = getDataPath();
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                entries.clear();
                for (int i = 0; i < arr.size(); i++) {
                    entries.add(GSON.fromJson(arr.get(i), MemoryEntry.class));
                }
            } catch (Exception e) {
                McBridgeMod.LOGGER.error("[mc-bridge] Failed to load memories: {}", e.getMessage());
            }
        }
    }

    private static synchronized void save() {
        try {
            JsonArray arr = new JsonArray();
            for (MemoryEntry e : entries) arr.add(GSON.toJsonTree(e));
            Path dir = getDataPath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Files.writeString(getDataPath(), GSON.toJson(arr));
        } catch (Exception ex) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save memories: {}", ex.getMessage());
        }
    }

    public static synchronized String addMemory(String content, String category, int importance, String tags) {
        var client = MinecraftClient.getInstance();
        MemoryEntry entry = new MemoryEntry();
        entry.id = UUID.randomUUID().toString().substring(0, 8);
        entry.time = Instant.now().toString();
        entry.content = content;
        entry.category = (category != null && !category.isEmpty()) ? category : "note";
        entry.importance = Math.max(1, Math.min(5, importance));
        entry.tags = tags != null ? tags : "";
        if (client.player != null) {
            var pos = client.player.getPos();
            entry.x = pos.x;
            entry.y = pos.y;
            entry.z = pos.z;
        }
        if (client.world != null) {
            entry.dimension = client.world.getRegistryKey().getValue().toString();
        }
        entries.add(entry);
        save();

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("id", entry.id);
        return r.toString();
    }

    public static synchronized String recallMemories(String query, String category, int limit) {
        JsonArray results = new JsonArray();
        int count = 0;
        for (int i = entries.size() - 1; i >= 0 && count < limit; i--) {
            MemoryEntry e = entries.get(i);
            boolean match = true;
            if (query != null && !query.isEmpty()) {
                match = e.content.toLowerCase().contains(query.toLowerCase())
                        || (e.tags != null && e.tags.toLowerCase().contains(query.toLowerCase()));
            }
            if (category != null && !category.isEmpty()) {
                match = match && e.category != null && e.category.equals(category);
            }
            if (match) {
                results.add(GSON.toJsonTree(e));
                count++;
            }
        }

        JsonObject r = new JsonObject();
        r.addProperty("total", results.size());
        r.add("memories", results);
        return r.toString();
    }

    public static synchronized String findNear(double x, double y, double z, double radius, int limit) {
        JsonArray results = new JsonArray();
        int count = 0;
        double radiusSq = radius * radius;
        for (int i = entries.size() - 1; i >= 0 && count < limit; i--) {
            MemoryEntry e = entries.get(i);
            double dx = e.x - x;
            double dy = e.y - y;
            double dz = e.z - z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= radiusSq) {
                JsonObject obj = GSON.toJsonTree(e).getAsJsonObject();
                obj.addProperty("distance", Math.sqrt(distSq));
                results.add(obj);
                count++;
            }
        }

        JsonObject r = new JsonObject();
        r.addProperty("total", results.size());
        r.add("memories", results);
        return r.toString();
    }

    public static synchronized List<MemoryEntry> getAllMemories() {
        return new ArrayList<>(entries);
    }

    public static synchronized String listMemories(String category, int limit) {
        return recallMemories(null, category, limit > 0 ? limit : 50);
    }

    public static synchronized String deleteMemory(String id) {
        boolean removed = entries.removeIf(e -> e.id.equals(id));
        if (removed) save();
        JsonObject r = new JsonObject();
        r.addProperty("ok", removed);
        return r.toString();
    }

    public static synchronized String summarize() {
        JsonObject r = new JsonObject();
        r.addProperty("total", entries.size());
        r.add("byCategory", categoryBreakdown());
        if (!entries.isEmpty()) {
            MemoryEntry latest = entries.get(entries.size() - 1);
            String content = latest.content;
            r.addProperty("latest", content != null ? content.substring(0, Math.min(100, content.length())) : "");
        }
        return r.toString();
    }

    private static JsonObject categoryBreakdown() {
        JsonObject cats = new JsonObject();
        for (MemoryEntry e : entries) {
            int count = cats.has(e.category) ? cats.get(e.category).getAsInt() : 0;
            cats.addProperty(e.category, count + 1);
        }
        return cats;
    }
}
