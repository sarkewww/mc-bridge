package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.List;
import java.util.Locale;

public class ChatHandler {

    public static String handleChat(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("msg")) throw new Exception("Missing 'msg' parameter");
        String msg = json.get("msg").getAsString();
        if (msg.startsWith("/")) {
            player.networkHandler.sendChatCommand(msg.substring(1));
        } else {
            InterceptState.runBypass(() -> player.networkHandler.sendChatMessage(msg));
        }
        return "sent: " + msg;
    }

    public static String handleSend(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("msg")) throw new Exception("Missing 'msg' parameter");
        String msg = json.get("msg").getAsString();
        InterceptState.runBypass(() -> player.networkHandler.sendChatMessage(msg));
        return "ok: " + msg;
    }

    public static String handleIntercept(JsonObject json) {
        JsonObject result = new JsonObject();
        boolean changed = false;
        if (json.has("mode")) {
            String mode = json.get("mode").getAsString().toLowerCase();
            switch (mode) {
                case "off" -> InterceptState.setMode(InterceptState.Mode.OFF);
                case "copy" -> InterceptState.setMode(InterceptState.Mode.COPY);
                case "intercept", "block" -> InterceptState.setMode(InterceptState.Mode.INTERCEPT);
                default -> { return errorJson("Unknown mode: " + mode + " (off/copy/intercept)"); }
            }
            changed = true;
        } else if (json.has("enable")) {
            boolean enable = json.get("enable").getAsBoolean();
            InterceptState.setEnabled(enable);
            changed = true;
        } else if (json.has("toggle")) {
            InterceptState.toggle();
            changed = true;
        }
        if (changed) Config.save();
        result.addProperty("enabled", InterceptState.isEnabled());
        result.addProperty("mode", InterceptState.getMode().name().toLowerCase());
        result.addProperty("message", switch (InterceptState.getMode()) {
            case OFF -> "Chat intercept is OFF";
            case COPY -> "Copy mode: messages are sent and recorded";
            case INTERCEPT -> "Block mode: messages are intercepted and forwarded";
        });
        return result.toString();
    }

    public static String handleChatlog(JsonObject json) {
        int count = json.has("count") ? json.get("count").getAsInt() : 50;
        String player = json.has("player") ? json.get("player").getAsString().toLowerCase(Locale.ROOT) : null;
        String keyword = json.has("keyword") ? json.get("keyword").getAsString().toLowerCase(Locale.ROOT) : null;
        List<String> msgs = ChatLog.getRecent(count);
        if (player != null || keyword != null) {
            msgs = msgs.stream()
                .filter(m -> {
                    String lower = m.toLowerCase(Locale.ROOT);
                    if (keyword != null && !lower.contains(keyword)) return false;
                    if (player != null && !lower.contains("<" + player + ">")) return false;
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());
        }
        JsonObject result = new JsonObject();
        result.addProperty("total", ChatLog.size());
        result.addProperty("displayed", msgs.size());
        JsonArray arr = new JsonArray();
        for (String m : msgs) arr.add(m);
        result.add("messages", arr);
        return result.toString();
    }

    public static String handleClearChat(MinecraftClient client) {
        if (client.inGameHud == null) return "{\"ok\":false,\"error\":\"HUD not loaded\"}";
        client.inGameHud.getChatHud().clear(false);
        return "{\"ok\":true}";
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }
}
