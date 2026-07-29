package com.example.mcbridge;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TravelLogService {

    private static volatile boolean recording = false;
    private static Thread thread = null;
    private static final Queue<PositionRecord> log = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECORDS = 5000;
    private static final int INTERVAL_MS = 30000;

    public static void start() {
        if (recording) return;
        recording = true;
        thread = new Thread(() -> {
            while (recording) {
                try {
                    var client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null && client.player != null) {
                        var pos = client.player.getPos();
                        var dim = client.player.getWorld().getRegistryKey().getValue().toString();
                        log.add(new PositionRecord(
                                Instant.now().toString(),
                                round(pos.x, 1),
                                round(pos.y, 1),
                                round(pos.z, 1),
                                dim
                        ));
                        while (log.size() > MAX_RECORDS) {
                            log.poll();
                        }
                    }
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    McBridgeMod.LOGGER.error("[mc-bridge] TravelLog error: {}", e.getMessage());
                }
            }
        }, "TravelLogThread");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        recording = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    public static List<PositionRecord> getEntries() {
        return new ArrayList<>(log);
    }

    public static List<PositionRecord> getRecent(int limit) {
        List<PositionRecord> all = new ArrayList<>(log);
        if (all.size() <= limit) return all;
        return all.subList(all.size() - limit, all.size());
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    public record PositionRecord(String timestamp, double x, double y, double z, String dimension) {}
}
