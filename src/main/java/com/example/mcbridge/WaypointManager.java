package com.example.mcbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaypointManager {

    private static final Path CONFIG_DIR = Path.of(
            System.getProperty("user.home"),
            ".config", "mc-bridge"
    );
    private static final Path FILE = CONFIG_DIR.resolve("waypoints.json");

    private static final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();

    static {
        load();
    }

    public static void add(String name, double x, double y, double z, String dimension) {
        waypoints.put(name, new Waypoint(name, x, y, z, dimension));
        save();
    }

    public static void remove(String name) {
        waypoints.remove(name);
        save();
    }

    public static Waypoint get(String name) {
        return waypoints.get(name);
    }

    public static Collection<Waypoint> getAll() {
        return waypoints.values();
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                String content = Files.readString(FILE);
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                for (var e : arr) {
                    try {
                        JsonObject obj = e.getAsJsonObject();
                        if (!obj.has("name") || !obj.has("x") || !obj.has("y") || !obj.has("z")) continue;
                        String name = obj.get("name").getAsString();
                        double x = obj.get("x").getAsDouble();
                        double y = obj.get("y").getAsDouble();
                        double z = obj.get("z").getAsDouble();
                        String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
                        waypoints.put(name, new Waypoint(name, x, y, z, dim));
                    } catch (Exception ignored) { /* skip malformed entry */ }
                }
            }
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to load waypoints: {}", e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonArray arr = new JsonArray();
            for (Waypoint wp : waypoints.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", wp.name);
                obj.addProperty("x", wp.x);
                obj.addProperty("y", wp.y);
                obj.addProperty("z", wp.z);
                obj.addProperty("dimension", wp.dimension);
                arr.add(obj);
            }
            java.nio.file.Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, arr.toString());
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Failed to save waypoints: {}", e.getMessage());
        }
    }

    public record Waypoint(String name, double x, double y, double z, String dimension) {}
}
