package com.example.mcbridge;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScreenshotRepeat {
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mc-bridge-screenshot-repeat");
        t.setDaemon(true);
        return t;
    });
    private static volatile ScheduledFuture<?> task;
    private static volatile int totalTaken = 0;
    private static volatile boolean running = false;
    private static volatile int targetCount = 0;

    public static String handle(String action, int intervalSec, int count, MinecraftClient client) {
        JsonObject r = new JsonObject();
        switch (action) {
            case "start" -> {
                if (running) {
                    r.addProperty("error", "Already running");
                    return r.toString();
                }
                running = true;
                totalTaken = 0;
                targetCount = count;
                task = scheduler.scheduleAtFixedRate(() -> {
                    if (targetCount > 0 && totalTaken >= targetCount) {
                        stop();
                        return;
                    }
                    takeScreenshot(client);
                    totalTaken++;
                }, 0, intervalSec, TimeUnit.SECONDS);
                r.addProperty("started", true);
                r.addProperty("interval", intervalSec);
                r.addProperty("count", count);
            }
            case "stop" -> {
                stop();
                r.addProperty("stopped", true);
                r.addProperty("taken", totalTaken);
            }
            default -> {
                r.addProperty("running", running);
                r.addProperty("taken", totalTaken);
                r.addProperty("target", targetCount);
            }
        }
        return r.toString();
    }

    private static synchronized void takeScreenshot(MinecraftClient client) {
        try {
            client.execute(() -> {
                try {
                    var fb = client.getFramebuffer();
                    if (fb == null) return;
                    String filename = "repeat_" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
                        .format(LocalDateTime.now()) + ".png";
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                        client.runDirectory,
                        filename,
                        fb,
                        text -> McBridgeMod.LOGGER.info("[mc-bridge] Screenshot repeat: {}", text.getString())
                    );
                } catch (Exception e) {
                    McBridgeMod.LOGGER.error("[mc-bridge] Screenshot repeat failed: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            McBridgeMod.LOGGER.error("[mc-bridge] Screenshot repeat failed: {}", e.getMessage());
        }
    }

    private static synchronized void stop() {
        running = false;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    public static boolean isRunning() {
        return running;
    }
}
