package com.example.mcbridge;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerContext {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ServerEntry> cache = new ConcurrentHashMap<>();
    private static String currentBrand = "unknown";

    static {
        load();
    }

    public static class ServerEntry {
        public String brand;
        public String networkType; // "major_network" or "small_server"
        public String displayName;
        public String firstSeen;
        public String lastSeen;
        public String motdCache;
        public int maxPlayers;

        public ServerEntry() {}
    }

    private static Path getRegistryPath() {
        return Path.of(
                MinecraftClient.getInstance().runDirectory.getPath(),
                "config", "mc-bridge-profiles", "_servers.json"
        );
    }

    private static void load() {
        Path path = getRegistryPath();
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                for (String key : obj.keySet()) {
                    cache.put(key, GSON.fromJson(obj.get(key), ServerEntry.class));
                }
            } catch (Exception e) {
                McBridgeMod.LOGGER.error("[mc-bridge] Failed to load _servers.json: {}", e.getMessage());
            }
        }
    }

    public static synchronized void save() {
        try {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, ServerEntry> e : cache.entrySet()) {
                obj.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            }
            Files.createDirectories(getRegistryPath().getParent());
            Files.writeString(getRegistryPath(), GSON.toJson(obj));
        } catch (Exception ex) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save _servers.json: {}", ex.getMessage());
        }
    }

    public static synchronized ServerEntry getOrCreate(String address) {
        return cache.computeIfAbsent(address, addr -> {
            ServerEntry e = new ServerEntry();
            e.brand = "unknown";
            e.networkType = "unknown";
            e.displayName = addr;
            e.firstSeen = Instant.now().toString();
            e.lastSeen = e.firstSeen;
            e.maxPlayers = 0;
            save();
            return e;
        });
    }

    public static synchronized void setServerInfo(String address, String brand, String networkType, String displayName, int maxPlayers) {
        ServerEntry e = getOrCreate(address);
        e.brand = brand;
        e.networkType = networkType;
        e.displayName = displayName;
        e.maxPlayers = maxPlayers;
        e.lastSeen = Instant.now().toString();
        save();
    }

    public static synchronized String getBrand(String address) {
        ServerEntry e = cache.get(address);
        return e != null ? e.brand : null;
    }

    public static synchronized String getNetworkType(String address) {
        ServerEntry e = cache.get(address);
        return e != null ? e.networkType : null;
    }

    public static synchronized String getNetworkTypeForBrand(String brand) {
        for (ServerEntry e : cache.values()) {
            if (brand.equals(e.brand)) return e.networkType;
        }
        return "major_network";
    }

    public static synchronized boolean needsAnalysis(String address) {
        ServerEntry e = cache.get(address);
        return e == null || "unknown".equals(e.brand);
    }

    public static synchronized String getCurrentServerInfoJson() {
        JsonObject result = new JsonObject();
        var client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            ServerInfo si = client.getCurrentServerEntry();
            String addr = si.address;
            result.addProperty("address", addr);
            result.addProperty("motd", si.label != null ? si.label.getString() : "");
            result.addProperty("needsAnalysis", needsAnalysis(addr));
            String brand = getBrand(addr);
            result.addProperty("brand", brand != null ? brand : "unknown");
            currentBrand = brand != null ? brand : "unknown";
        } else {
            result.addProperty("address", "");
            result.addProperty("needsAnalysis", false);
            result.addProperty("brand", "offline");
            currentBrand = "offline";
        }
        return result.toString();
    }

    public static synchronized String getCurrentBrand() {
        return currentBrand;
    }

    public static synchronized String getAllServersJson() {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, ServerEntry> e : cache.entrySet()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("brand", e.getValue().brand);
            entry.addProperty("networkType", e.getValue().networkType);
            entry.addProperty("displayName", e.getValue().displayName);
            entry.addProperty("firstSeen", e.getValue().firstSeen);
            entry.addProperty("lastSeen", e.getValue().lastSeen);
            entry.addProperty("maxPlayers", e.getValue().maxPlayers);
            result.add(e.getKey(), entry);
        }
        return result.toString();
    }

    public static synchronized void refreshCurrentBrand() {
        var client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            String addr = client.getCurrentServerEntry().address;
            String brand = getBrand(addr);
            currentBrand = brand != null ? brand : "unknown";
        } else {
            currentBrand = "offline";
        }
    }
}
