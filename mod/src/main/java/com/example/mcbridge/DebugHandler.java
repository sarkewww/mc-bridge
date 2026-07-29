package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Scanner;
import java.io.File;

public class DebugHandler {

    public static String handleLogs(JsonObject json, net.minecraft.client.MinecraftClient client) throws Exception {
        File runDir = client.runDirectory;
        File logFile = new File(runDir, "logs/latest.log");
        if (!logFile.exists()) {
            throw new Exception("latest.log not found at " + logFile.getAbsolutePath());
        }

        int lines = json.has("lines") ? json.get("lines").getAsInt() : 50;

        try (Scanner scanner = new Scanner(logFile, "UTF-8")) {
            ArrayDeque<String> queue = new ArrayDeque<>(lines + 1);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                queue.addLast(line);
                if (queue.size() > lines) queue.removeFirst();
            }
            JsonObject result = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String line : queue) arr.add(line);
            result.add("lines", arr);
            result.addProperty("total", queue.size());
            result.addProperty("file", logFile.getAbsolutePath());
            return result.toString();
        }
    }

    // --- mc_packet_logger ---
    public static String handlePacketLogger(JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString() : "status";
        JsonObject result = new JsonObject();

        switch (action) {
            case "start" -> {
                PacketLoggerService.startLogging();
                result.addProperty("status", "logging");
                result.addProperty("info", "Packet logging started");
            }
            case "stop" -> {
                PacketLoggerService.stopLogging();
                result.addProperty("status", "stopped");
                result.addProperty("stats", PacketLoggerService.getStats());
            }
            case "status" -> {
                result.addProperty("status", PacketLoggerService.isLogging() ? "logging" : "stopped");
                result.addProperty("stats", PacketLoggerService.getStats());
            }
            default -> result.addProperty("error", "Unknown action: " + action);
        }

        return result.toString();
    }

    // --- mc_packet_logger_detail ---
    public static String handlePacketLoggerDetail(JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString() : "status";
        String filter = json.has("filter") ? json.get("filter").getAsString() : "";
        JsonObject result = new JsonObject();

        switch (action) {
            case "start" -> {
                PacketLoggerService.startDetailLogging(filter);
                result.addProperty("status", "detail_logging");
                result.addProperty("filter", filter.isEmpty() ? "all" : filter);
            }
            case "stop" -> {
                PacketLoggerService.stopDetailLogging();
                result.addProperty("status", "stopped");
            }
            case "status" -> {
                result.addProperty("status", PacketLoggerService.isDetailLogging() ? "detail_logging" : "stopped");
                result.addProperty("filter", PacketLoggerService.getDetailFilter());
            }
            default -> result.addProperty("error", "Unknown action: " + action);
        }

        return result.toString();
    }

    // --- mc_packet_logger_find ---
    public static String handlePacketLoggerFind(JsonObject json) {
        String query = json.has("query") ? json.get("query").getAsString() : "";
        String direction = json.has("direction") ? json.get("direction").getAsString() : "";
        int limit = json.has("limit") ? json.get("limit").getAsInt() : 20;

        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("direction", direction.isEmpty() ? "all" : direction);
        result.addProperty("limit", limit);

        JsonArray found = PacketLoggerService.search(query, direction, limit);
        result.add("results", found);
        result.addProperty("count", found.size());

        return result.toString();
    }
}
