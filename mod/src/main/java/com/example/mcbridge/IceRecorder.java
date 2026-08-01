package com.example.mcbridge;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IceRecorder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Frame> frames = Collections.synchronizedList(new ArrayList<>());
    private long startTime;
    private double targetX, targetZ;
    private volatile boolean active = false;

    static class Frame {
        int tick;
        double x, z;
        float yaw;
        String action;
        int wpIndex;
        double distToTarget;
        double speed;
        long timestamp;
    }

    void start(double tx, double tz) {
        frames.clear();
        startTime = System.currentTimeMillis();
        targetX = tx;
        targetZ = tz;
        active = true;
    }

    void record(double x, double z, float yaw, IcePhysicsEngine.Action act, int wpIdx, double dist) {
        if (!active) return;
        Frame f = new Frame();
        f.tick = (int)((System.currentTimeMillis() - startTime) / 50);
        f.x = x; f.z = z; f.yaw = yaw;
        f.action = act.name();
        f.wpIndex = wpIdx;
        f.distToTarget = dist;
        f.speed = 0;
        f.timestamp = System.currentTimeMillis();
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            f.speed = Math.hypot(client.player.getVelocity().x, client.player.getVelocity().z);
        }
        frames.add(f);
        if (frames.size() > 5000) frames.subList(0, 500).clear();
    }

    void stop(String reason) {
        if (!active) return;
        active = false;
        save(reason);
    }

    private void save(String reason) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("reason", reason);
            obj.addProperty("target_x", targetX);
            obj.addProperty("target_z", targetZ);
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault()).format(Instant.now());
            obj.addProperty("start_time", Instant.ofEpochMilli(startTime).toString());
            obj.addProperty("save_time", Instant.now().toString());
            obj.addProperty("frame_count", frames.size());

            JsonArray arr = new JsonArray();
            for (Frame f : frames) {
                JsonObject fj = new JsonObject();
                fj.addProperty("tick", f.tick);
                fj.addProperty("x", round(f.x, 2));
                fj.addProperty("z", round(f.z, 2));
                fj.addProperty("yaw", round(f.yaw, 1));
                fj.addProperty("action", f.action);
                fj.addProperty("wp", f.wpIndex);
                fj.addProperty("dist", round(f.distToTarget, 2));
                fj.addProperty("speed", round(f.speed, 4));
                arr.add(fj);
            }
            obj.add("frames", arr);

            Path dir = Path.of(MinecraftClient.getInstance().runDirectory.getPath(),
                    "config", "mc-bridge-profiles", "iceboat_blackbox");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("bb_" + ts + ".json"), GSON.toJson(obj));
            McBridgeMod.LOGGER.info("[mc-bridge] Blackbox saved: {} frames", frames.size());
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Blackbox save failed: {}", e.getMessage());
        }
    }

    private static double round(double v, int d) {
        double f = Math.pow(10, d);
        return Math.round(v * f) / f;
    }
}
