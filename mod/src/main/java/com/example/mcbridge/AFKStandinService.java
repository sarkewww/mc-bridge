package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AFKStandinService {

    private static volatile boolean enabled = false;
    private static long lastActivityTime = System.currentTimeMillis();
    private static final long AFK_THRESHOLD_MS = 60_000;
    private static final Queue<AfkMessage> pendingMessages = new ConcurrentLinkedQueue<>();
    private static final Set<String> repliedMessageIds = new HashSet<>();
    private static final Set<String> pendingMessageKeys = new HashSet<>();

    public static void updateActivity() {
        if (enabled) {
            lastActivityTime = System.currentTimeMillis();
        }
    }

    public static void enable() {
        enabled = true;
        lastActivityTime = System.currentTimeMillis();
        pendingMessages.clear();
        pendingMessageKeys.clear();
        repliedMessageIds.clear();
        McBridgeMod.LOGGER.info("[mc-bridge] AFK standin enabled");
    }

    public static void disable() {
        enabled = false;
        pendingMessages.clear();
        McBridgeMod.LOGGER.info("[mc-bridge] AFK standin disabled");
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isAfk() {
        return enabled && (System.currentTimeMillis() - lastActivityTime) > AFK_THRESHOLD_MS;
    }

    public static void onIncomingChat(String sender, String message) {
        if (!enabled) return;
        String dedupKey = sender + "|" + message;
        if (repliedMessageIds.contains(dedupKey)) return;
        if (pendingMessageKeys.contains(dedupKey)) return;
        String messageId = dedupKey + "|" + System.nanoTime();
        pendingMessageKeys.add(dedupKey);
        pendingMessages.add(new AfkMessage(messageId, sender, message, Instant.now().toString()));
        if (pendingMessages.size() > 100) {
            AfkMessage evicted = pendingMessages.poll();
            if (evicted != null) pendingMessageKeys.remove(evicted.sender + "|" + evicted.message);
        }
    }

    public static String handleStatus() {
        JsonObject result = new JsonObject();
        result.addProperty("enabled", enabled);
        result.addProperty("afk", isAfk());
        result.addProperty("idleSeconds", (System.currentTimeMillis() - lastActivityTime) / 1000);
        result.addProperty("pendingCount", pendingMessages.size());
        JsonArray msgs = new JsonArray();
        for (AfkMessage m : pendingMessages) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", m.id);
            entry.addProperty("sender", m.sender);
            entry.addProperty("message", m.message);
            entry.addProperty("time", m.time);
            msgs.add(entry);
        }
        result.add("pending", msgs);
        return result.toString();
    }

    public static void markReplied(String messageId) {
        pendingMessages.removeIf(m -> {
            if (m.id.equals(messageId)) {
                repliedMessageIds.add(m.sender + "|" + m.message);
                pendingMessageKeys.remove(m.sender + "|" + m.message);
                return true;
            }
            return false;
        });
        if (repliedMessageIds.size() > 5000) {
            var iter = repliedMessageIds.iterator();
            for (int i = 0; i < 2500 && iter.hasNext(); i++) { iter.next(); iter.remove(); }
        }
    }

    public static String handleLearn(JsonObject json, MinecraftClient client) {
        int count = json.has("count") ? json.get("count").getAsInt() : 50;
        var messages = ChatLog.getOwnMessages(count);
        JsonArray arr = new JsonArray();
        for (var msg : messages) {
            arr.add(msg);
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", arr.size());
        result.add("messages", arr);
        return result.toString();
    }

    public record AfkMessage(String id, String sender, String message, String time) {}
}
