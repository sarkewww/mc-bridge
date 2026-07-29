package com.example.mcbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerTrackerService {

    private static Thread thread = null;
    private static volatile boolean running = false;
    private static final int INTERVAL_MS = 30000;
    private static final Set<String> previouslyOnline = new HashSet<>();
    private static final Object lock = new Object();

    public static void start() {
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            while (running) {
                try {
                    tick();
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    McBridgeMod.LOGGER.error("[mc-bridge] PlayerTracker error: {}", e.getMessage());
                }
            }
        }, "PlayerTrackerThread");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(5000); } catch (InterruptedException ignored) {}
            thread = null;
        }
        // cleanup on main thread after thread is fully stopped
        MinecraftClient.getInstance().execute(() -> {
            synchronized (lock) {
                String brand = ServerContext.getCurrentBrand();
                for (String name : previouslyOnline) {
                    PlayerProfileManager.recordOffline(name, brand);
                }
                previouslyOnline.clear();
                PlayerProfileManager.saveAll();
            }
        });
    }

    public static boolean isRunning() {
        return running;
    }

    public static Set<String> getOnlineNames() {
        synchronized (lock) {
            return new HashSet<>(previouslyOnline);
        }
    }

    private static void tick() {
        var client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                synchronized (lock) {
                    var networkHandler = client.getNetworkHandler();
                    if (networkHandler == null) return;
                    var playerList = networkHandler.getPlayerList();
                    if (playerList == null) return;

                    String brand = ServerContext.getCurrentBrand();
                    Set<String> currentlyOnline = new HashSet<>();
                    List<String> onlineNames = new ArrayList<>();

                    for (var entry : playerList) {
                        String name = entry.getProfile().getName();
                        if (name == null || name.isEmpty()) continue;
                        currentlyOnline.add(name);
                        onlineNames.add(name);
                        if (!previouslyOnline.contains(name)) {
                            PlayerProfileManager.recordOnline(name, brand);
                        }
                    }

                    for (String name : previouslyOnline) {
                        if (!currentlyOnline.contains(name)) {
                            PlayerProfileManager.recordOffline(name, brand);
                        }
                    }

                    var world = client.world;
                    if (world != null) {
                        for (var playerEntity : world.getPlayers()) {
                            String name = playerEntity.getName().getString();
                            var pos = playerEntity.getPos();
                            PlayerProfileManager.recordPosition(name,
                                    round(pos.x, 1), round(pos.y, 1), round(pos.z, 1),
                                    world.getRegistryKey().getValue().toString(), brand);
                        }
                    }

                    previouslyOnline.clear();
                    previouslyOnline.addAll(currentlyOnline);
                }
            } catch (Exception e) {
                McBridgeMod.LOGGER.error("[mc-bridge] PlayerTracker tick error: {}", e.getMessage());
            }
        });
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
