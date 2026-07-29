package com.example.mcbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.math.Box;

public class AutoFish {
    private static Thread fishThread;
    private static volatile boolean running = false;
    private static int caught = 0;
    private static int castCount = 0;

    public static synchronized String start(MinecraftClient client) {
        if (running) throw new RuntimeException("AutoFish already running");

        running = true;
        caught = 0;
        castCount = 0;

        fishThread = new Thread(() -> {
            try {
                while (running) {
                    if (client.world == null || client.player == null) {
                        Thread.sleep(1000);
                        continue;
                    }

                    // Cast fishing rod (right-click)
                    client.execute(() -> {
                        var interaction = client.interactionManager;
                        if (interaction != null && client.player != null) {
                            interaction.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
                        }
                    });
                    castCount++;
                    Thread.sleep(600);

                    // Wait for bite: poll bobber state
                    long startTime = System.currentTimeMillis();
                    while (running && System.currentTimeMillis() - startTime < 45000) {
                        if (!running) break;
                        FishingBobberEntity[] bobberRef = {null};
                        client.execute(() -> bobberRef[0] = findBobber(client));
                        FishingBobberEntity bobber = bobberRef[0];
                        if (bobber == null) {
                            // Bobber gone = fish caught or rod retrieved
                            if (System.currentTimeMillis() - startTime > 2000) {
                                break;
                            }
                            Thread.sleep(100);
                            continue;
                        }
                        // Check if bobber is moving erratically (fish bite)
                        final FishingBobberEntity finalBobber = bobber;
                        boolean[] bite = {false};
                        client.execute(() -> bite[0] = isBite(finalBobber));
                        if (bite[0]) {
                            Thread.sleep(200);
                            // Reel in
                            client.execute(() -> {
                                var interaction = client.interactionManager;
                                if (interaction != null && client.player != null) {
                                    interaction.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
                                }
                            });
                            caught++;
                            Thread.sleep(1500);
                            break;
                        }
                        Thread.sleep(150);
                    }

                    // Timeout: reel in and re-cast
                    if (running) {
                        client.execute(() -> {
                            var interaction = client.interactionManager;
                            if (interaction != null && client.player != null) {
                                interaction.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
                            }
                        });
                        Thread.sleep(2000);
                    }
                }
            } catch (InterruptedException e) {
                // Stopped
            } finally {
                running = false;
            }
        }, "mc-bridge-autofish");
        fishThread.setDaemon(true);
        fishThread.start();

        return "{\"started\": true}";
    }

    public static synchronized String stop() {
        running = false;
        if (fishThread != null) {
            fishThread.interrupt();
            fishThread = null;
        }
        String result = "{\"stopped\": true, \"caught\": " + caught + ", \"casts\": " + castCount + "}";
        caught = 0;
        castCount = 0;
        return result;
    }

    public static synchronized String status() {
        return "{\"running\": " + running + ", \"caught\": " + caught + ", \"casts\": " + castCount + "}";
    }

    public static boolean isRunning() {
        return running;
    }

    private static FishingBobberEntity findBobber(MinecraftClient client) {
        var world = client.world;
        var player = client.player;
        if (world == null || player == null) return null;
        Box searchBox = player.getBoundingBox().expand(32);
        for (var entity : world.getEntitiesByClass(FishingBobberEntity.class, searchBox, e -> e != null && e.getOwner() == player)) {
            return entity;
        }
        return null;
    }

    private static boolean isBite(FishingBobberEntity bobber) {
        var vel = bobber.getVelocity();
        double speed = vel.length();
        if (speed > 0.15) return true;
        if (bobber.isRemoved()) return true;
        return false;
    }
}
