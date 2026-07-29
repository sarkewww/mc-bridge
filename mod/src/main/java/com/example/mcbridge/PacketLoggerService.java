package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class PacketLoggerService {

    private static volatile boolean logging = false;
    private static volatile boolean detailLogging = false;
    private static volatile String detailFilter = "";
    private static final Map<String, Integer> packetStats = new LinkedHashMap<>();
    private static final Queue<PacketRecord> packetHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_HISTORY = 5000;

    public static void startLogging() {
        logging = true;
    }

    public static void stopLogging() {
        logging = false;
    }

    public static boolean isLogging() {
        return logging;
    }

    public static void startDetailLogging(String filter) {
        detailLogging = true;
        detailFilter = filter;
    }

    public static void stopDetailLogging() {
        detailLogging = false;
    }

    public static boolean isDetailLogging() {
        return detailLogging;
    }

    public static String getDetailFilter() {
        return detailFilter;
    }

    public static synchronized void recordPacket(String direction, String packetName, String content) {
        if (!logging && !detailLogging) return;

        packetStats.merge(packetName, 1, Integer::sum);

        if (detailLogging && (detailFilter.isEmpty() || packetName.toLowerCase().contains(detailFilter.toLowerCase()))) {
            PacketRecord record = new PacketRecord(
                    Instant.now().toString(),
                    direction,
                    packetName,
                    content != null ? content : ""
            );
            packetHistory.add(record);
            while (packetHistory.size() > MAX_HISTORY) {
                packetHistory.poll();
            }
        }
    }

    public static synchronized String getStats() {
        JsonObject stats = new JsonObject();
        stats.addProperty("totalTypes", packetStats.size());
        int total = packetStats.values().stream().mapToInt(Integer::intValue).sum();
        stats.addProperty("totalPackets", total);

        JsonArray types = new JsonArray();
        packetStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .forEach(e -> {
                    JsonObject t = new JsonObject();
                    t.addProperty("packet", e.getKey());
                    t.addProperty("count", e.getValue());
                    types.add(t);
                });
        stats.add("types", types);
        return stats.toString();
    }

    public static synchronized JsonArray search(String query, String direction, int limit) {
        JsonArray results = new JsonArray();
        int count = 0;
        for (PacketRecord record : packetHistory) {
            if (count >= limit) break;
            if (!direction.isEmpty() && !record.direction.equalsIgnoreCase(direction)) continue;
            if (!query.isEmpty() && !record.packetName.toLowerCase().contains(query.toLowerCase())
                    && !record.content.toLowerCase().contains(query.toLowerCase())) continue;

            JsonObject r = new JsonObject();
            r.addProperty("time", record.timestamp);
            r.addProperty("direction", record.direction);
            r.addProperty("packet", record.packetName);
            if (!record.content.isEmpty()) {
                r.addProperty("content", record.content);
            }
            results.add(r);
            count++;
        }
        return results;
    }

    private record PacketRecord(String timestamp, String direction, String packetName, String content) {}
}
