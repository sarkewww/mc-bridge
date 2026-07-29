package com.example.mcbridge;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class PlayerProfileManager {

    private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlayerProfile> serverCache = new ConcurrentHashMap<>();
    private static final Map<String, PlayerProfile> globalCache = new ConcurrentHashMap<>();
    private static final int MAX_CHAT_HISTORY = 1000;
    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static volatile JsonObject cachedIndex = null;
    private static boolean dirtyIndex = false;

    static {
        try {
            Files.createDirectories(getBaseDir());
            Files.createDirectories(getBaseDir().resolve("_global"));
        } catch (IOException e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to create profiles dir: {}", e.getMessage());
        }
    }

    public static class PlayerProfile {
        public String name;
        public String firstSeen;
        public String lastSeen;
        public List<ChatEntry> chatHistory;
        public List<OnlineSession> onlineSessions;
        public List<PositionEntry> knownPositions;
        public Map<String, Integer> interactions;
        public List<EquipmentSnapshot> equipmentSnapshots;
        public boolean pendingAnalysis;
        public String analysisGeneratedAt;
    }

    public static class ChatEntry {
        public String time;
        public String content;
        public String direction;
        public String target;
    }

    public static class OnlineSession {
        public String start;
        public String end;
    }

    public static class PositionEntry {
        public String time;
        public double x, y, z;
        public String dimension;
    }

    public static class EquipmentSnapshot {
        public String time;
        public String mainHand;
        public String offHand;
        public String helmet, chestplate, leggings, boots;
    }

    private static Path getBaseDir() {
        return Path.of(
                MinecraftClient.getInstance().runDirectory.getPath(),
                "config", "mc-bridge-profiles"
        );
    }

    private static Path serverDir(String brand) {
        return getBaseDir().resolve("servers").resolve(sanitizeBrand(brand));
    }

    private static Path globalDir() {
        return getBaseDir().resolve("_global");
    }

    private static Path indexFile() {
        return getBaseDir().resolve("_index.json");
    }

    private static String sanitizeBrand(String brand) {
        return brand.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // --- Server-scoped profile ---
    private static PlayerProfile getOrCreateServer(String name, String brand) {
        String key = brand + ":" + name;
        return serverCache.computeIfAbsent(key, k -> {
            Path path = serverDir(brand).resolve(name + ".json");
            if (Files.exists(path)) {
                try {
                    return GSON.fromJson(Files.readString(path), PlayerProfile.class);
                } catch (Exception e) {
                    McBridgeMod.LOGGER.error("[mc-bridge] Failed to load {} profile for {}: {}", brand, name, e.getMessage());
                }
            }
            PlayerProfile p = new PlayerProfile();
            p.name = name;
            p.firstSeen = Instant.now().toString();
            p.chatHistory = new ArrayList<>();
            p.onlineSessions = new ArrayList<>();
            p.knownPositions = new ArrayList<>();
            p.interactions = new HashMap<>();
            p.equipmentSnapshots = new ArrayList<>();
            return p;
        });
    }

    private static void saveServer(String name, String brand) {
        PlayerProfile p = serverCache.get(brand + ":" + name);
        if (p == null) return;
        try {
            Path dir = serverDir(brand);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".json"), GSON.toJson(p));
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save {} profile for {}: {}", brand, name, e.getMessage());
        }
    }

    // --- Global profile ---
    private static PlayerProfile getOrCreateGlobal(String name) {
        return globalCache.computeIfAbsent(name, n -> {
            Path path = globalDir().resolve(n + ".json");
            if (Files.exists(path)) {
                try {
                    return GSON.fromJson(Files.readString(path), PlayerProfile.class);
                } catch (Exception e) {
                    McBridgeMod.LOGGER.error("[mc-bridge] Failed to load global profile for {}: {}", n, e.getMessage());
                }
            }
            PlayerProfile p = new PlayerProfile();
            p.name = n;
            p.firstSeen = Instant.now().toString();
            p.chatHistory = new ArrayList<>();
            p.onlineSessions = new ArrayList<>();
            p.knownPositions = new ArrayList<>();
            p.interactions = new HashMap<>();
            p.equipmentSnapshots = new ArrayList<>();
            return p;
        });
    }

    private static void saveGlobal(String name) {
        PlayerProfile p = globalCache.get(name);
        if (p == null) return;
        try {
            Files.createDirectories(globalDir());
            Files.writeString(globalDir().resolve(name + ".json"), GSON.toJson(p));
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save global profile for {}: {}", name, e.getMessage());
        }
    }

    // --- _index.json ---
    private static JsonObject loadIndex() {
        if (cachedIndex != null) return cachedIndex;
        Path path = indexFile();
        if (Files.exists(path)) {
            try {
                cachedIndex = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                return cachedIndex;
            } catch (Exception e) {
                McBridgeMod.LOGGER.error("[mc-bridge] Failed to load _index.json: {}", e.getMessage());
            }
        }
        cachedIndex = new JsonObject();
        return cachedIndex;
    }

    private static void saveIndex(JsonObject index) {
        if (index != cachedIndex) { cachedIndex = index; }
        try {
            Files.createDirectories(getBaseDir());
            Files.writeString(indexFile(), GSON.toJson(index));
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save _index.json: {}", e.getMessage());
        }
        dirtyIndex = false;
    }

    private static void invalidateIndexCache() {
        cachedIndex = null;
    }

    private static void updateIndexEntry(String name, String brand, boolean isPromoted) {
        JsonObject index = loadIndex();
        JsonObject entry;
        if (index.has(name)) {
            entry = index.get(name).getAsJsonObject();
        } else {
            entry = new JsonObject();
            entry.addProperty("firstSeen", Instant.now().toString());
            entry.addProperty("totalMessages", 0);
            entry.addProperty("totalInteractions", 0);
            entry.addProperty("pendingAnalysis", false);
            index.add(name, entry);
        }
        entry.addProperty("lastSeen", Instant.now().toString());

        JsonArray servers = entry.has("serversAppeared") ? entry.get("serversAppeared").getAsJsonArray() : new JsonArray();
        boolean found = false;
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getAsString().equals(brand)) {
                found = true;
                break;
            }
        }
        if (!found) servers.add(brand);
        entry.add("serversAppeared", servers);

        if (isPromoted) {
            entry.addProperty("promotedToGlobal", true);
            entry.addProperty("pendingAnalysis", true);
        }

        dirtyIndex = true;
    }

    // --- Promotion check ---
    private static void checkAndPromote(String name, String brand) {
        loadIndex();
        if (!cachedIndex.has(name)) return;
        JsonObject entry = cachedIndex.get(name).getAsJsonObject();
        if (entry.has("promotedToGlobal") && entry.get("promotedToGlobal").getAsBoolean()) return;

        String networkType = ServerContext.getNetworkTypeForBrand(brand);
        boolean shouldPromote = false;

        if ("small_server".equals(networkType)) {
            shouldPromote = true;
        } else {
            JsonArray servers = entry.getAsJsonArray("serversAppeared");
            Set<String> uniqueBrands = new HashSet<>();
            if (servers != null) {
                for (int i = 0; i < servers.size(); i++) {
                    uniqueBrands.add(servers.get(i).getAsString());
                }
            }
            if (uniqueBrands.size() >= 2) {
                shouldPromote = true;
            }
        }

        if (shouldPromote) {
            doPromote(name);
            entry.addProperty("promotedToGlobal", true);
            entry.addProperty("pendingAnalysis", true);
            dirtyIndex = true;
        }
    }

    private static void doPromote(String name) {
        if (globalCache.containsKey(name)) return;
        PlayerProfile merged = new PlayerProfile();
        merged.name = name;
        String earliestFirstSeen = null;
        merged.chatHistory = new ArrayList<>();
        merged.onlineSessions = new ArrayList<>();
        merged.knownPositions = new ArrayList<>();
        merged.interactions = new HashMap<>();
        merged.equipmentSnapshots = new ArrayList<>();
        merged.pendingAnalysis = true;

        // Merge all server profiles into global
        for (Map.Entry<String, PlayerProfile> e : serverCache.entrySet()) {
            String[] parts = e.getKey().split(":", 2);
            if (parts.length == 2 && parts[1].equals(name)) {
                PlayerProfile sp = e.getValue();
                if (sp.chatHistory != null) merged.chatHistory.addAll(sp.chatHistory);
                if (sp.onlineSessions != null) merged.onlineSessions.addAll(sp.onlineSessions);
                if (sp.knownPositions != null) merged.knownPositions.addAll(sp.knownPositions);
                if (sp.interactions != null) {
                    for (Map.Entry<String, Integer> ie : sp.interactions.entrySet()) {
                        merged.interactions.merge(ie.getKey(), ie.getValue(), Integer::sum);
                    }
                }
                if (sp.equipmentSnapshots != null) merged.equipmentSnapshots.addAll(sp.equipmentSnapshots);
                if (sp.firstSeen != null) {
                    if (earliestFirstSeen == null || sp.firstSeen.compareTo(earliestFirstSeen) < 0) {
                        earliestFirstSeen = sp.firstSeen;
                    }
                }
            }
        }
        merged.firstSeen = earliestFirstSeen != null ? earliestFirstSeen : Instant.now().toString();

        globalCache.put(name, merged);
        saveGlobal(name);
        McBridgeMod.LOGGER.info("[mc-bridge] Promoted {} to global profile", name);
    }

    // --- Record methods (all take brand parameter) ---
    public static void recordMessage(String playerName, String content, String direction, String target, String brand) {
        rwLock.writeLock().lock();
        try {
            PlayerProfile p = getOrCreateServer(playerName, brand);
            ChatEntry entry = new ChatEntry();
            entry.time = Instant.now().toString();
            entry.content = content;
            entry.direction = direction;
            entry.target = target;
            p.chatHistory.add(entry);
            if (p.chatHistory.size() > MAX_CHAT_HISTORY) {
                p.chatHistory.remove(0);
            }
            dirty.set(true);
            updateIndexEntry(playerName, brand, false);
            checkAndPromote(playerName, brand);

            // Also update global if promoted
            String globalKey = brand + ":" + playerName;
            if (isPromoted(playerName)) {
                PlayerProfile gp = getOrCreateGlobal(playerName);
                gp.chatHistory.add(entry);
                if (gp.chatHistory.size() > MAX_CHAT_HISTORY) {
                    gp.chatHistory.remove(0);
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static void recordOnline(String name, String brand) {
        rwLock.writeLock().lock();
        try {
            PlayerProfile p = getOrCreateServer(name, brand);
            OnlineSession session = new OnlineSession();
            session.start = Instant.now().toString();
            p.onlineSessions.add(session);
            p.lastSeen = session.start;
            dirty.set(true);
            updateIndexEntry(name, brand, false);

            if (isPromoted(name)) {
                PlayerProfile gp = getOrCreateGlobal(name);
                OnlineSession gs = new OnlineSession();
                gs.start = session.start;
                gp.onlineSessions.add(gs);
                gp.lastSeen = session.start;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static void recordOffline(String name, String brand) {
        rwLock.writeLock().lock();
        try {
            PlayerProfile p = serverCache.get(brand + ":" + name);
            if (p == null || p.onlineSessions.isEmpty()) return;
            OnlineSession last = p.onlineSessions.get(p.onlineSessions.size() - 1);
            if (last.end == null) {
                last.end = Instant.now().toString();
            }
            dirty.set(true);

            if (isPromoted(name)) {
                PlayerProfile gp = globalCache.get(name);
                if (gp != null && !gp.onlineSessions.isEmpty()) {
                    OnlineSession gs = gp.onlineSessions.get(gp.onlineSessions.size() - 1);
                    if (gs.end == null) gs.end = last.end;
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static void recordPosition(String name, double x, double y, double z, String dim, String brand) {
        rwLock.writeLock().lock();
        try {
            PlayerProfile p = getOrCreateServer(name, brand);
            PositionEntry pos = new PositionEntry();
            pos.time = Instant.now().toString();
            pos.x = x;
            pos.y = y;
            pos.z = z;
            pos.dimension = dim;
            p.knownPositions.add(pos);
            dirty.set(true);
            updateIndexEntry(name, brand, false);

            if (isPromoted(name)) {
                PlayerProfile gp = getOrCreateGlobal(name);
                gp.knownPositions.add(pos);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static void recordInteraction(String from, String to, String brand) {
        rwLock.writeLock().lock();
        try {
            PlayerProfile p = getOrCreateServer(from, brand);
            p.interactions.merge(to, 1, Integer::sum);
            p.lastSeen = Instant.now().toString();
            dirty.set(true);
            updateIndexEntry(from, brand, false);
            checkAndPromote(from, brand);

            if (globalCache.containsKey(from) || isPromoted(from)) {
                PlayerProfile gp = getOrCreateGlobal(from);
                gp.interactions.merge(to, 1, Integer::sum);
                gp.lastSeen = Instant.now().toString();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private static boolean isPromoted(String name) {
        loadIndex();
        return cachedIndex.has(name) && cachedIndex.get(name).getAsJsonObject().has("promotedToGlobal")
                && cachedIndex.get(name).getAsJsonObject().get("promotedToGlobal").getAsBoolean();
    }

    // --- Query methods ---
    public static String getRawProfile(String name) {
        rwLock.readLock().lock();
        try {
            // Prefer global if promoted
            if (isPromoted(name) || globalCache.containsKey(name)) {
                return GSON.toJson(getOrCreateGlobal(name));
            }
            String brand = ServerContext.getCurrentBrand();
            return GSON.toJson(getOrCreateServer(name, brand));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static String getRawProfileForServer(String name, String brand) {
        rwLock.readLock().lock();
        try {
            return GSON.toJson(getOrCreateServer(name, brand));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static String getIndexJson() {
        rwLock.readLock().lock();
        try {
            return GSON.toJson(loadIndex());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static List<String> getRecentMessages(String name, int limit) {
        rwLock.readLock().lock();
        try {
            String brand = ServerContext.getCurrentBrand();
            PlayerProfile p = serverCache.get(brand + ":" + name);
            if (p == null || p.chatHistory == null || p.chatHistory.isEmpty()) return List.of();
            int start = Math.max(0, p.chatHistory.size() - limit);
            return p.chatHistory.subList(start, p.chatHistory.size()).stream()
                    .map(e -> "[" + e.direction + "] " + e.content)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static String getSelfName() {
        rwLock.readLock().lock();
        try {
            var client = MinecraftClient.getInstance();
            return client.player != null ? client.player.getName().getString() : "Self";
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static void saveAll() {
        rwLock.writeLock().lock();
        try {
            if (!dirty.get() && !dirtyIndex) return;
            for (String key : serverCache.keySet()) {
                String[] parts = key.split(":", 2);
                if (parts.length == 2) saveServer(parts[1], parts[0]);
            }
            for (String key : globalCache.keySet()) {
                saveGlobal(key);
            }
            if (dirtyIndex && cachedIndex != null) {
                saveIndex(cachedIndex);
            }
            dirty.set(false);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static boolean isDirty() {
        return dirty.get();
    }

    public static String getPendingAnalysisProfilesJson() {
        rwLock.readLock().lock();
        try {
            JsonArray arr = new JsonArray();
            JsonObject index = loadIndex();
            for (String name : index.keySet()) {
                JsonObject entry = index.get(name).getAsJsonObject();
                if (entry.has("pendingAnalysis") && entry.get("pendingAnalysis").getAsBoolean()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("name", name);
                    item.addProperty("firstSeen", entry.has("firstSeen") ? entry.get("firstSeen").getAsString() : "");
                    item.addProperty("totalMessages", entry.has("totalMessages") ? entry.get("totalMessages").getAsInt() : 0);
                    arr.add(item);
                }
            }
            return arr.toString();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static void markAnalysisDone(String name) {
        rwLock.writeLock().lock();
        try {
            JsonObject index = loadIndex();
            if (index.has(name)) {
                index.get(name).getAsJsonObject().addProperty("pendingAnalysis", false);
                index.get(name).getAsJsonObject().addProperty("analysisGeneratedAt", Instant.now().toString());
                saveIndex(index);
            }
            if (globalCache.containsKey(name)) {
                globalCache.get(name).pendingAnalysis = false;
                globalCache.get(name).analysisGeneratedAt = Instant.now().toString();
                saveGlobal(name);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
