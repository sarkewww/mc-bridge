package com.example.mcbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketBridge extends WebSocketServer {

    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;
    private static final long TASK_TIMEOUT_MS = 15_000;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public WebSocketBridge(int port) {
        super(new InetSocketAddress("127.0.0.1", port));
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        McBridgeMod.LOGGER.info("[mc-bridge] Client connected: {}", conn.getRemoteSocketAddress());

        JsonObject resp = new JsonObject();
        resp.addProperty("ok", true);
        resp.addProperty("data", "connected");
        conn.send(resp.toString());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        McBridgeMod.LOGGER.info("[mc-bridge] Client disconnected: {}", reason);
        if (conn.getAttachment() != null) {
            conn.setAttachment(null);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message.length() > MAX_MESSAGE_SIZE) {
            sendError(conn, null, "Message too large");
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();
            String id = json.has("rid") ? json.get("rid").getAsString() : (json.has("id") ? json.get("id").getAsString() : null);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                sendError(conn, id, "Minecraft client not available");
                return;
            }

            // Permission check (permission command is always allowed)
            final String cmdType = type;
            if (!cmdType.equals("permission") && !PermissionManager.isAllowed(cmdType)) {
                sendError(conn, id, "Command '" + cmdType + "' is not in the whitelist. Permission system is enabled.");
                return;
            }

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicBoolean timedOut = new AtomicBoolean(false);

            // run_script runs on background thread (it may block for long periods)
            if (cmdType.equals("run_script")) {
                new Thread(() -> {
                    try {
                        String result = CommandHandler.handle(cmdType, json, client);
                        if (!timedOut.get()) sendResult(conn, id, result);
                    } catch (Exception e) {
                        McBridgeMod.LOGGER.error("[mc-bridge] Script error: {}", e.getMessage());
                        if (!timedOut.get()) sendError(conn, id, e.getMessage());
                    }
                    completed.set(true);
                }, "mc-bridge-script-runner").start();
            } else {
                client.execute(() -> {
                    if (timedOut.get()) return;
                    try {
                        String result = CommandHandler.handle(cmdType, json, client);
                        if (!timedOut.get()) sendResult(conn, id, result);
                    } catch (Exception e) {
                        McBridgeMod.LOGGER.error("[mc-bridge] Command error: {}", e.getMessage());
                        if (!timedOut.get()) sendError(conn, id, e.getMessage());
                    }
                    completed.set(true);
                });
            }
            scheduler.schedule(() -> {
                if (!completed.get()) {
                    timedOut.set(true);
                    McBridgeMod.LOGGER.warn("[mc-bridge] Task '{}' timed out after {}ms", cmdType, TASK_TIMEOUT_MS);
                    sendError(conn, id, "Command '" + cmdType + "' timed out after " + TASK_TIMEOUT_MS + "ms");
                }
            }, cmdType.equals("run_script") ? 300_000 : TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (com.google.gson.JsonSyntaxException e) {
            sendError(conn, null, "Invalid JSON: " + e.getMessage());
        } catch (Exception e) {
            sendError(conn, null, "Parse error: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        McBridgeMod.LOGGER.error("[mc-bridge] WebSocket error: {}", ex.getMessage());
    }

    @Override
    public void onStart() {
        McBridgeMod.LOGGER.info("[mc-bridge] WebSocket server listening on ws://127.0.0.1:{}", getPort());
    }

    private void sendResult(WebSocket conn, String id, String data) {
        if (conn == null || !conn.isOpen()) return;
        try {
            JsonObject resp = new JsonObject();
            if (id != null) resp.addProperty("id", id);
            resp.addProperty("ok", true);
            resp.addProperty("data", data);
            conn.send(resp.toString());
        } catch (Exception ignored) {}
    }

    private void sendError(WebSocket conn, String id, String error) {
        if (conn == null || !conn.isOpen()) return;
        try {
            JsonObject resp = new JsonObject();
            if (id != null) resp.addProperty("id", id);
            resp.addProperty("ok", false);
            resp.addProperty("error", error);
            conn.send(resp.toString());
        } catch (Exception ignored) {}
    }
}
