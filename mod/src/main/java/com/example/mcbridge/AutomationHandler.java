package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.GameOptions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AutomationHandler {

    private static final long TASK_TIMEOUT_MS = 15_000;

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }

    @SuppressWarnings("unchecked")
    public static String handleBaritone() {
        JsonObject result = new JsonObject();
        result.addProperty("loaded", false);

        Optional<ModContainer> baritoneMod = FabricLoader.getInstance().getModContainer("baritone");
        if (baritoneMod.isEmpty()) {
            result.addProperty("found", false);
            result.addProperty("error", "Baritone mod not installed");
            return result.toString();
        }
        result.addProperty("found", true);

        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getProvider = apiClass.getMethod("getProvider");
            Object provider = getProvider.invoke(null);
            Method getPrimary = provider.getClass().getMethod("getPrimaryBaritone");
            Object baritone = getPrimary.invoke(provider);
            result.addProperty("loaded", true);

            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            boolean isPathing = (boolean) pathingBehavior.getClass().getMethod("isPathing").invoke(pathingBehavior);
            result.addProperty("isPathing", isPathing);

            try {
                Object customGoalProcess = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
                Object goal = customGoalProcess.getClass().getMethod("getGoal").invoke(customGoalProcess);
                result.addProperty("goal", goal != null ? goal.toString() : "none");
            } catch (Exception e) {
                result.addProperty("goal", "not available");
            }

            try {
                Object builderProcess = baritone.getClass().getMethod("getBuilderProcess").invoke(baritone);
                boolean isActive = (boolean) builderProcess.getClass().getMethod("isActive").invoke(builderProcess);
                result.addProperty("builderActive", isActive);
            } catch (Exception e) {
                result.addProperty("builderActive", false);
            }

            try {
                Object mineProcess = baritone.getClass().getMethod("getMineProcess").invoke(baritone);
                boolean mining = (boolean) mineProcess.getClass().getMethod("isActive").invoke(mineProcess);
                result.addProperty("mining", mining);
            } catch (Exception e) {
                result.addProperty("mining", false);
            }

            try {
                Object followProcess = baritone.getClass().getMethod("getFollowProcess").invoke(baritone);
                boolean following = (boolean) followProcess.getClass().getMethod("isActive").invoke(followProcess);
                result.addProperty("following", following);
            } catch (Exception e) {
                result.addProperty("following", false);
            }

        } catch (ClassNotFoundException e) {
            result.addProperty("loaded", false);
            result.addProperty("error", "Baritone API class not found: " + e.getMessage());
        } catch (Exception e) {
            result.addProperty("loaded", false);
            result.addProperty("error", "Failed to access Baritone: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return result.toString();
    }

    @SuppressWarnings("unchecked")
    public static String handleWurst() {
        JsonObject result = new JsonObject();

        Optional<ModContainer> wurstMod = FabricLoader.getInstance().getModContainer("wurst");
        if (wurstMod.isEmpty()) {
            wurstMod = FabricLoader.getInstance().getModContainer("Wurst");
        }
        if (wurstMod.isEmpty()) {
            result.addProperty("found", false);
            result.addProperty("error", "Wurst mod not installed");
            return result.toString();
        }
        result.addProperty("found", true);

        try {
            Class<?> wurstClient = Class.forName("net.wurstclient.WurstClient");

            Object instance = null;
            try {
                Field instField = wurstClient.getField("INSTANCE");
                instance = instField.get(null);
            } catch (NoSuchFieldException e) {
                try {
                    Method getInstance = wurstClient.getMethod("getInstance");
                    instance = getInstance.invoke(null);
                } catch (NoSuchMethodException e2) {
                    result.addProperty("loaded", false);
                    result.addProperty("error", "Cannot access WurstClient instance");
                    return result.toString();
                }
            }

            result.addProperty("loaded", true);

            try {
                Object hackList = instance.getClass().getMethod("getHacks").invoke(instance);
                Object enabledHacks = hackList.getClass().getMethod("getEnabledHacks").invoke(hackList);
                List<String> hackNames = new ArrayList<>();

                if (enabledHacks instanceof List) {
                    for (Object hack : (List<?>) enabledHacks) {
                        hackNames.add(hack.getClass().getSimpleName());
                    }
                } else if (enabledHacks instanceof Iterable) {
                    for (Object hack : (Iterable<?>) enabledHacks) {
                        hackNames.add(hack.getClass().getSimpleName());
                    }
                } else {
                    result.addProperty("enabledHacks_raw", enabledHacks.toString());
                }

                JsonArray hacksArray = new JsonArray();
                for (String name : hackNames) {
                    hacksArray.add(name);
                }
                result.add("enabledHacks", hacksArray);
            } catch (Exception e) {
                result.addProperty("enabledHacksError", e.getMessage());
            }

            try {
                Object features = instance.getClass().getMethod("getFeatures").invoke(instance);
                result.addProperty("featuresType", features.getClass().getName());
            } catch (Exception e) {
            }

            try {
                Method isEnabledMethod = instance.getClass().getMethod("isEnabled");
                boolean isEnabled = (boolean) isEnabledMethod.invoke(instance);
                result.addProperty("wurstEnabled", isEnabled);
            } catch (Exception e) {
            }

        } catch (ClassNotFoundException e) {
            result.addProperty("loaded", false);
            result.addProperty("error", "WurstClient class not found");
        } catch (Exception e) {
            result.addProperty("loaded", false);
            result.addProperty("error", "Failed to access Wurst: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return result.toString();
    }

    public static String handleAutoFish(JsonObject json, MinecraftClient client) {
        String action = json.has("action") ? json.get("action").getAsString() : "toggle";
        return switch (action) {
            case "start" -> AutoFish.start(client);
            case "stop" -> AutoFish.stop();
            default -> AutoFish.status();
        };
    }

    public static String handleAutoExplore(JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString() : "status";
        return switch (action) {
            case "start" -> {
                int radius = json.has("radius") ? json.get("radius").getAsInt() : 100;
                String mode = json.has("mode") ? json.get("mode").getAsString() : "spiral";
                yield AutoExploreService.start(radius, mode);
            }
            case "stop" -> AutoExploreService.stop();
            case "data" -> AutoExploreService.getExplorationData();
            default -> AutoExploreService.status();
        };
    }

    public static String handleWalkTo(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z"))
            throw new Exception("Missing coordinates (x, y, z)");
        double tx = json.get("x").getAsDouble();
        double ty = json.get("y").getAsDouble();
        double tz = json.get("z").getAsDouble();

        if (isBaritoneLoaded()) {
            String cmd = "#goto " + (int)tx + " " + (int)ty + " " + (int)tz;
            player.networkHandler.sendChatMessage(cmd);
            JsonObject r = new JsonObject();
            r.addProperty("status", "walking");
            r.addProperty("method", "baritone");
            r.addProperty("command", cmd);
            r.addProperty("target_x", tx); r.addProperty("target_y", ty); r.addProperty("target_z", tz);
            return r.toString();
        }

        double threshold = json.has("threshold") ? json.get("threshold").getAsDouble() : 1.5;
        boolean sprint = json.has("sprint") && json.get("sprint").getAsBoolean();
        var options = client.options;
        client.execute(() -> {
            options.forwardKey.setPressed(true);
            if (sprint) options.sprintKey.setPressed(true);
        });
        Thread walkThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    boolean[] arrived = {false};
                    client.execute(() -> {
                        try {
                            if (player != null && !player.isRemoved()) {
                                double dx = tx - player.getX();
                                double dz = tz - player.getZ();
                                double dy = ty - player.getY();
                                double dist = Math.sqrt(dx * dx + dz * dz);
                                if (dist > 0.01) {
                                    player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
                                    player.setPitch((float) -Math.toDegrees(Math.atan2(dy, dist)));
                                }
                                arrived[0] = player.getPos().squaredDistanceTo(tx, ty, tz) < threshold * threshold;
                            }
                        } catch (Exception ignored) {}
                    });
                    if (arrived[0]) break;
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                client.execute(() -> {
                    options.forwardKey.setPressed(false);
                    if (sprint) options.sprintKey.setPressed(false);
                });
            }
        });
        walkThread.setDaemon(true);
        walkThread.start();
        JsonObject r = new JsonObject();
        r.addProperty("status", "walking");
        r.addProperty("method", "custom");
        r.addProperty("target_x", tx); r.addProperty("target_y", ty); r.addProperty("target_z", tz);
        r.addProperty("threshold", threshold);
        return r.toString();
    }

    private static boolean isBaritoneLoaded() {
        try {
            return FabricLoader.getInstance().getModContainer("baritone").isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public static String handlePressKey(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("key")) throw new Exception("Missing 'key' parameter");
        String keyName = json.get("key").getAsString().toLowerCase();
        KeyBinding binding = resolveKeyBinding(client.options, keyName);
        if (binding == null) throw new Exception("Unknown key: " + keyName);
        boolean state = json.has("state") ? json.get("state").getAsBoolean() : true;
        if (json.has("duration")) {
            int duration = json.get("duration").getAsInt();
            if (duration <= 0) throw new Exception("Duration must be positive (ms)");
            binding.setPressed(true);
            final KeyBinding releaseBinding = binding;
            new Thread(() -> {
                try { Thread.sleep(duration); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                client.execute(() -> releaseBinding.setPressed(false));
            }).start();
            return "{\"key\":\"" + keyName + "\",\"pressed\":true,\"duration\":" + duration + "}";
        }
        binding.setPressed(state);
        return "{\"key\":\"" + keyName + "\",\"pressed\":" + state + "}";
    }

    private static KeyBinding resolveKeyBinding(GameOptions options, String name) {
        KeyBinding named = namedKeyBinding(options, name);
        if (named != null) return named;
        if (name.startsWith("hotbar_") || name.startsWith("slot_")) {
            try {
                int idx = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                if (idx >= 1 && idx <= 9) return options.hotbarKeys[idx - 1];
            } catch (NumberFormatException e) {}
        }
        for (KeyBinding kb : options.allKeys) {
            if (name.equals(kb.getBoundKeyTranslationKey()) || name.equals(kb.getTranslationKey())) {
                return kb;
            }
        }
        return null;
    }

    private static KeyBinding namedKeyBinding(GameOptions options, String name) {
        return switch (name) {
            case "forward" -> options.forwardKey;
            case "back" -> options.backKey;
            case "left" -> options.leftKey;
            case "right" -> options.rightKey;
            case "jump" -> options.jumpKey;
            case "sneak" -> options.sneakKey;
            case "sprint" -> options.sprintKey;
            case "attack" -> options.attackKey;
            case "use" -> options.useKey;
            case "drop" -> options.dropKey;
            case "inventory" -> options.inventoryKey;
            case "swap_hands", "swap" -> options.swapHandsKey;
            case "chat" -> options.chatKey;
            case "command" -> options.commandKey;
            case "player_list", "list" -> options.playerListKey;
            case "screenshot" -> options.screenshotKey;
            case "fullscreen" -> options.fullscreenKey;
            case "perspective" -> options.togglePerspectiveKey;
            case "advancements" -> options.advancementsKey;
            case "spectator_outlines" -> options.spectatorOutlinesKey;
            case "smooth_camera" -> options.smoothCameraKey;
            case "save_toolbar" -> options.saveToolbarActivatorKey;
            case "load_toolbar" -> options.loadToolbarActivatorKey;
            default -> null;
        };
    }

    public static String handleUseItem(MinecraftClient client) throws Exception {
        if (client.interactionManager == null || client.player == null)
            throw new Exception("Not in game");
        var options = client.options;
        options.useKey.setPressed(true);
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            client.execute(() -> options.useKey.setPressed(false));
        }).start();
        return "{\"used_item\":true}";
    }

    public static String handleBedrockBreaker(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        if (!json.has("x") || !json.has("y") || !json.has("z"))
            return errorJson("Missing coordinates (x, y, z)");

        int bx = json.get("x").getAsInt();
        int by = json.get("y").getAsInt();
        int bz = json.get("z").getAsInt();
        int attempts = json.has("attempts") ? json.get("attempts").getAsInt() : 5;

        JsonObject result = new JsonObject();
        result.addProperty("target", bx + " " + by + " " + bz);
        result.addProperty("attempts", attempts);
        result.addProperty("warning", "This may trigger anti-cheat. Use at your own risk.");
        JsonArray results = new JsonArray();
        result.add("attemptsLog", results);

        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        BlockPos targetPos = new BlockPos(bx, by, bz);
        String blockBefore = world.getBlockState(targetPos).getBlock().toString();
        result.addProperty("blockBefore", blockBefore);

        Direction dir = Direction.UP;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < attempts; i++) {
                try {
                    CompletableFuture<?> cf = new CompletableFuture<>();
                    client.execute(() -> {
                        try {
                            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, dir));
                        } catch (Exception ignored) {}
                        cf.complete(null);
                    });
                    cf.get();
                    Thread.sleep(5);

                    CompletableFuture<?> cf2 = new CompletableFuture<>();
                    client.execute(() -> {
                        try {
                            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, dir));
                        } catch (Exception ignored) {}
                        cf2.complete(null);
                    });
                    cf2.get();
                    Thread.sleep(2);

                    CompletableFuture<?> cf3 = new CompletableFuture<>();
                    client.execute(() -> {
                        try {
                            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, dir));
                        } catch (Exception ignored) {}
                        cf3.complete(null);
                    });
                    cf3.get();
                    Thread.sleep(5);

                    CompletableFuture<?> cf4 = new CompletableFuture<>();
                    client.execute(() -> {
                        try {
                            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, targetPos, dir));
                            player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                        } catch (Exception ignored) {}
                        cf4.complete(null);
                    });
                    cf4.get();
                    Thread.sleep(10);

                    JsonObject attempt = new JsonObject();
                    attempt.addProperty("attempt", i + 1);
                    attempt.addProperty("sent", true);
                    results.add(attempt);
                } catch (Exception e) {
                    JsonObject attempt = new JsonObject();
                    attempt.addProperty("attempt", i + 1);
                    attempt.addProperty("sent", false);
                    attempt.addProperty("error", e.getMessage());
                    results.add(attempt);
                }
            }
        });

        try { future.get(TASK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (Exception e) {
            result.addProperty("error", "Bedrock breaker timed out or failed: " + e.getMessage());
        }

        String blockAfter = world.getBlockState(targetPos).getBlock().toString();
        result.addProperty("blockAfter", blockAfter);
        result.addProperty("success", !blockBefore.equals(blockAfter));

        return result.toString();
    }

    public static String handleMirror(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        String axis = json.get("axis").getAsString();
        boolean odd = json.has("center");
        boolean even = json.has("center1") && json.has("center2");

        int sourceMin = json.get("source_min").getAsInt();
        int sourceMax = json.get("source_max").getAsInt();
        int y1 = json.get("y1").getAsInt();
        int y2 = json.get("y2").getAsInt();
        int z1 = json.get("z1").getAsInt();
        int z2 = json.get("z2").getAsInt();
        int x1 = json.has("x1") ? json.get("x1").getAsInt() : z1;
        int x2 = json.has("x2") ? json.get("x2").getAsInt() : z2;

        double center;
        if (odd) {
            center = json.get("center").getAsInt();
        } else if (even) {
            int c1 = json.get("center1").getAsInt();
            int c2 = json.get("center2").getAsInt();
            center = (c1 + c2) / 2.0;
        } else {
            throw new Exception("Missing 'center' (odd) or 'center1'+'center2' (even)");
        }

        int mirrored = 0;
        int skipped = 0;

        if (axis.equals("z")) {
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    for (int z = sourceMin; z <= sourceMax; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) { skipped++; continue; }
                        int mirrorCoord = (int)Math.round(2 * center - z);
                        BlockPos mirrorPos = new BlockPos(x, y, mirrorCoord);
                        if (mirrorPos.equals(pos)) { skipped++; continue; }
                        world.setBlockState(mirrorPos, state, 3);
                        mirrored++;
                    }
                }
            }
        } else if (axis.equals("y")) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    for (int y = sourceMin; y <= sourceMax; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) { skipped++; continue; }
                        int mirrorCoord = (int)Math.round(2 * center - y);
                        BlockPos mirrorPos = new BlockPos(x, mirrorCoord, z);
                        if (mirrorPos.equals(pos)) { skipped++; continue; }
                        world.setBlockState(mirrorPos, state, 3);
                        mirrored++;
                    }
                }
            }
        } else {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    for (int x = sourceMin; x <= sourceMax; x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) { skipped++; continue; }
                        int mirrorCoord = (int)Math.round(2 * center - x);
                        BlockPos mirrorPos = new BlockPos(mirrorCoord, y, z);
                        if (mirrorPos.equals(pos)) { skipped++; continue; }
                        world.setBlockState(mirrorPos, state, 3);
                        mirrored++;
                    }
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("mirrored", mirrored);
        result.addProperty("skipped", skipped);
        return result.toString();
    }

    public static String handleScreenshot(MinecraftClient client) {
        JsonObject result = new JsonObject();
        try {
            var fb = client.getFramebuffer();
            if (fb == null) throw new Exception("No framebuffer available");

            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                client.runDirectory,
                client.runDirectory.getName() + "_" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now()) + ".png",
                fb,
                text -> {
                    String msg = text.getString();
                    McBridgeMod.LOGGER.info("[mc-bridge] Screenshot: {}", msg);
                }
            );
            result.addProperty("saved", true);
            result.addProperty("directory", client.runDirectory.getAbsolutePath() + File.separator + "screenshots");
        } catch (Exception e) {
            try {
                client.execute(() -> {
                    var screenshotKey = client.options.screenshotKey;
                    while (screenshotKey.wasPressed()) { }
                    screenshotKey.setPressed(true);
                });
                new Thread(() -> {
                    try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    client.execute(() -> client.options.screenshotKey.setPressed(false));
                }).start();
                result.addProperty("saved", true);
                result.addProperty("method", "keybind_trigger");
            } catch (Exception e2) {
                result.addProperty("saved", false);
                result.addProperty("error", e2.getMessage());
            }
        }
        return result.toString();
    }

    public static String handleScreenshotRepeat(JsonObject json, MinecraftClient client) {
        String action = json.has("action") ? json.get("action").getAsString() : "status";
        int interval = json.has("interval") ? json.get("interval").getAsInt() : 60;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        return ScreenshotRepeat.handle(action, interval, count, client);
    }

    public static String handleRunScript(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("steps")) throw new Exception("Missing 'steps' array");
        JsonArray steps = json.getAsJsonArray("steps");
        JsonObject result = new JsonObject();
        result.addProperty("totalSteps", steps.size());
        try {
            executeSteps(steps, client, player, result);
        } catch (Exception e) {
            result.addProperty("status", "error");
            result.addProperty("error", e.getMessage());
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static void executeSteps(JsonArray steps, MinecraftClient client, ClientPlayerEntity player, JsonObject result) {
        int executed = 0;
        int failed = 0;

        for (int i = 0; i < steps.size(); i++) {
            JsonObject step = steps.get(i).getAsJsonObject();
            String type = step.has("type") ? step.get("type").getAsString() : "command";

            try {
                int count = step.has("count") ? step.get("count").getAsInt() : 1;

                if (step.has("steps") && type.equals("loop")) {
                    JsonArray body = step.getAsJsonArray("steps");
                    for (int c = 0; c < count; c++) {
                        JsonObject loopResult = new JsonObject();
                        executeSteps(body, client, player, loopResult);
                        int bodyExec = loopResult.has("executed") ? loopResult.get("executed").getAsInt() : 0;
                        int bodyFail = loopResult.has("failed") ? loopResult.get("failed").getAsInt() : 0;
                        executed += bodyExec;
                        failed += bodyFail;
                    }
                    continue;
                }

                if (type.equals("while") && step.has("steps")) {
                    JsonArray body = step.getAsJsonArray("steps");
                    int maxIter = step.has("maxIterations") ? step.get("maxIterations").getAsInt() : 100;
                    int iter = 0;
                    while (iter < maxIter) {
                        boolean[] condResult = {false};
                        CompletableFuture<?> condCf = new CompletableFuture<>();
                        client.execute(() -> {
                            try { condResult[0] = evaluateCondition(step, client, player); }
                            catch (Exception ignored) {}
                            condCf.complete(null);
                        });
                        condCf.get();
                        if (!condResult[0]) break;
                        JsonObject loopResult = new JsonObject();
                        executeSteps(body, client, player, loopResult);
                        int bodyExec = loopResult.has("executed") ? loopResult.get("executed").getAsInt() : 0;
                        int bodyFail = loopResult.has("failed") ? loopResult.get("failed").getAsInt() : 0;
                        executed += bodyExec;
                        failed += bodyFail;
                        iter++;
                    }
                    continue;
                }

                if (type.equals("if")) {
                    boolean[] condResult = {false};
                    CompletableFuture<?> condCf = new CompletableFuture<>();
                    client.execute(() -> {
                        try { condResult[0] = evaluateCondition(step, client, player); }
                        catch (Exception ignored) {}
                        condCf.complete(null);
                    });
                    condCf.get();
                    boolean conditionPassed = condResult[0];
                    if (conditionPassed && step.has("then")) {
                        JsonArray thenSteps = step.getAsJsonArray("then");
                        JsonObject branchResult = new JsonObject();
                        executeSteps(thenSteps, client, player, branchResult);
                        executed += branchResult.has("executed") ? branchResult.get("executed").getAsInt() : 0;
                        failed += branchResult.has("failed") ? branchResult.get("failed").getAsInt() : 0;
                    } else if (!conditionPassed && step.has("else")) {
                        JsonArray elseSteps = step.getAsJsonArray("else");
                        JsonObject branchResult = new JsonObject();
                        executeSteps(elseSteps, client, player, branchResult);
                        executed += branchResult.has("executed") ? branchResult.get("executed").getAsInt() : 0;
                        failed += branchResult.has("failed") ? branchResult.get("failed").getAsInt() : 0;
                    }
                    executed++;
                    continue;
                }

                boolean breakOuter = false;
                for (int c = 0; c < count && !breakOuter; c++) {
                    switch (type) {
                        case "command" -> {
                            if (!step.has("cmd")) throw new Exception("Step " + i + " missing 'cmd'");
                            String cmd = step.get("cmd").getAsString();
                            if (cmd.startsWith("/")) cmd = cmd.substring(1);
                            final String fcmd = cmd;
                            CompletableFuture<?> cf = new CompletableFuture<>();
                            client.execute(() -> {
                                try { if (player.networkHandler != null) player.networkHandler.sendChatCommand(fcmd); }
                                catch (Exception ignored) {}
                                cf.complete(null);
                            });
                            cf.get();
                        }
                        case "chat" -> {
                            if (!step.has("msg")) throw new Exception("Step " + i + " missing 'msg'");
                            String msg = step.get("msg").getAsString();
                            final String fmsg = msg;
                            CompletableFuture<?> cf = new CompletableFuture<>();
                            client.execute(() -> {
                                try { InterceptState.runBypass(() -> player.networkHandler.sendChatMessage(fmsg)); }
                                catch (Exception ignored) {}
                                cf.complete(null);
                            });
                            cf.get();
                        }
                        case "wait" -> {
                            int ms = step.has("ms") ? step.get("ms").getAsInt() : 1000;
                            Thread.sleep(ms);
                        }
                        case "condition" -> {
                            boolean[] passedRef = {true};
                            CompletableFuture<?> cf = new CompletableFuture<>();
                            client.execute(() -> {
                                try {
                                    boolean p = true;
                                    if (step.has("if_block")) {
                                        JsonObject b = step.getAsJsonObject("if_block");
                                        int bx = b.get("x").getAsInt();
                                        int by = b.get("y").getAsInt();
                                        int bz = b.get("z").getAsInt();
                                        String expected = b.has("block") ? b.get("block").getAsString().toLowerCase(Locale.ROOT) : "";
                                        var world = client.world;
                                        if (world == null) { passedRef[0] = false; cf.complete(null); return; }
                                        BlockPos bp = new BlockPos(bx, by, bz);
                                        String actual = world.getBlockState(bp).getBlock().toString().toLowerCase(Locale.ROOT);
                                        boolean match = expected.isEmpty() || actual.contains(expected.replace("minecraft:", ""));
                                        if (b.has("not") && b.get("not").getAsBoolean()) match = !match;
                                        if (!match) p = false;
                                    }
                                    if (step.has("if_slot")) {
                                        JsonObject s = step.getAsJsonObject("if_slot");
                                        int slotIdx = s.get("slot").getAsInt();
                                        String expectedItem = s.has("item") ? s.get("item").getAsString().toLowerCase(Locale.ROOT) : "";
                                        var screenHandler = player.currentScreenHandler;
                                        boolean match = false;
                                        if (slotIdx >= 0 && slotIdx < screenHandler.slots.size()) {
                                            ItemStack stack = screenHandler.slots.get(slotIdx).getStack();
                                            String actual = stack.getItem().toString().toLowerCase(Locale.ROOT);
                                            String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
                                            match = expectedItem.isEmpty()
                                                    || actual.contains(expectedItem.replace("minecraft:", ""))
                                                    || displayName.contains(expectedItem);
                                        }
                                        if (s.has("not") && s.get("not").getAsBoolean()) match = !match;
                                        if (!match) p = false;
                                    }
                                    if (step.has("if_distance")) {
                                        JsonObject d = step.getAsJsonObject("if_distance");
                                        double tx = d.get("x").getAsDouble();
                                        double ty = d.get("y").getAsDouble();
                                        double tz = d.get("z").getAsDouble();
                                        double dist = player.getPos().distanceTo(new Vec3d(tx, ty, tz));
                                        boolean match = true;
                                        if (d.has("max")) match = dist <= d.get("max").getAsDouble();
                                        if (d.has("min")) match = match && dist >= d.get("min").getAsDouble();
                                        if (d.has("not") && d.get("not").getAsBoolean()) match = !match;
                                        if (!match) p = false;
                                    }
                                    passedRef[0] = p;
                                } catch (Exception ignored) { passedRef[0] = false; }
                                cf.complete(null);
                            });
                            cf.get();
                            boolean passed = passedRef[0];
                            if (!passed) {
                                if (step.has("steps")) {
                                    breakOuter = true;
                                }
                                continue;
                            }
                        }
                        case "parallel" -> {
                            // Execute sequentially (all client ops go through single client thread anyway)
                            if (!step.has("steps")) throw new Exception("Step " + i + " missing 'steps' for parallel");
                            JsonArray parallelSteps = step.getAsJsonArray("steps");
                            for (int pi = 0; pi < parallelSteps.size(); pi++) {
                                JsonArray single = new JsonArray();
                                single.add(parallelSteps.get(pi));
                                JsonObject pr = new JsonObject();
                                executeSteps(single, client, player, pr);
                                executed += pr.has("executed") ? pr.get("executed").getAsInt() : 0;
                                failed += pr.has("failed") ? pr.get("failed").getAsInt() : 0;
                            }
                            continue;
                        }
                        default -> throw new Exception("Unknown step type: " + type);
                    }
                    executed++;
                }
                if (breakOuter) break;
            } catch (Exception e) {
                failed++;
                McBridgeMod.LOGGER.error("[mc-bridge] Script step {} failed: {}", i, e.getMessage());
            }
        }

        result.addProperty("status", "done");
        result.addProperty("executed", executed);
        result.addProperty("failed", failed);
    }

    private static boolean evaluateCondition(JsonObject step, MinecraftClient client, ClientPlayerEntity player) {
        boolean passed = true;

        if (step.has("if_block")) {
            JsonObject b = step.getAsJsonObject("if_block");
            int bx = b.get("x").getAsInt();
            int by = b.get("y").getAsInt();
            int bz = b.get("z").getAsInt();
            String expected = b.has("block") ? b.get("block").getAsString().toLowerCase(Locale.ROOT) : "";
            var world = client.world;
            if (world != null) {
                BlockPos bp = new BlockPos(bx, by, bz);
                String actual = world.getBlockState(bp).getBlock().toString().toLowerCase(Locale.ROOT);
                boolean match = expected.isEmpty() || actual.contains(expected.replace("minecraft:", ""));
                if (b.has("not") && b.get("not").getAsBoolean()) match = !match;
                if (!match) passed = false;
            }
        }

        if (step.has("if_slot")) {
            JsonObject s = step.getAsJsonObject("if_slot");
            int slotIdx = s.get("slot").getAsInt();
            String expectedItem = s.has("item") ? s.get("item").getAsString().toLowerCase(Locale.ROOT) : "";
            var screenHandler = player != null ? player.currentScreenHandler : null;
            if (screenHandler != null && slotIdx >= 0 && slotIdx < screenHandler.slots.size()) {
                ItemStack stack = screenHandler.slots.get(slotIdx).getStack();
                String actual = stack.getItem().toString().toLowerCase(Locale.ROOT);
                String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
                boolean match = expectedItem.isEmpty()
                        || actual.contains(expectedItem.replace("minecraft:", ""))
                        || displayName.contains(expectedItem);
                if (s.has("not") && s.get("not").getAsBoolean()) match = !match;
                if (!match) passed = false;
            }
        }

        if (step.has("if_distance")) {
            JsonObject d = step.getAsJsonObject("if_distance");
            double tx = d.get("x").getAsDouble();
            double ty = d.get("y").getAsDouble();
            double tz = d.get("z").getAsDouble();
            if (player != null) {
                double dist = player.getPos().distanceTo(new Vec3d(tx, ty, tz));
                boolean match = true;
                if (d.has("max")) match = dist <= d.get("max").getAsDouble();
                if (d.has("min")) match = match && dist >= d.get("min").getAsDouble();
                if (d.has("not") && d.get("not").getAsBoolean()) match = !match;
                if (!match) passed = false;
            }
        }

        if (step.has("condition")) {
            JsonObject cond = step.getAsJsonObject("condition");
            boolean condPassed = evaluateCondition(cond, client, player);
            if (!condPassed) passed = false;
        }

        return passed;
    }

    // --- Batch build optimization ---
    public static String handleBatchBuild(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("commands")) throw new Exception("Missing 'commands' array");
        JsonArray commands = json.getAsJsonArray("commands");
        JsonArray optimized = new JsonArray();
        JsonObject result = new JsonObject();
        result.addProperty("inputCount", commands.size());

        Map<String, List<int[]>> blocksByType = new HashMap<>();

        for (int i = 0; i < commands.size(); i++) {
            String cmd = commands.get(i).getAsString();
            String[] parts = cmd.split(" ");
            if (parts.length >= 5 && (parts[0].equals("/setblock") || parts[0].equals("setblock"))) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    String material = parts[4];
                    blocksByType.computeIfAbsent(material, k -> new ArrayList<>()).add(new int[]{x, y, z});
                } catch (Exception e) {
                    optimized.add(cmd);
                }
            } else {
                optimized.add(cmd);
            }
        }

        int optimizedCount = 0;
        for (var entry : blocksByType.entrySet()) {
            String material = entry.getKey();
            List<int[]> positions = entry.getValue();

            if (positions.size() < 4) {
                for (int[] p : positions) {
                    optimized.add("setblock " + p[0] + " " + p[1] + " " + p[2] + " " + material);
                }
                continue;
            }

            List<int[]> remaining = new ArrayList<>(positions);
            while (!remaining.isEmpty()) {
                int[] first = remaining.remove(0);
                int minX = first[0], maxX = first[0];
                int minY = first[1], maxY = first[1];
                int minZ = first[2], maxZ = first[2];

                boolean expanded;
                do {
                    expanded = false;
                    Iterator<int[]> it = remaining.iterator();
                    while (it.hasNext()) {
                        int[] p = it.next();
                        boolean adjacent = p[0] >= minX - 1 && p[0] <= maxX + 1
                                && p[1] >= minY - 1 && p[1] <= maxY + 1
                                && p[2] >= minZ - 1 && p[2] <= maxZ + 1;
                        if (adjacent) {
                            minX = Math.min(minX, p[0]);
                            maxX = Math.max(maxX, p[0]);
                            minY = Math.min(minY, p[1]);
                            maxY = Math.max(maxY, p[1]);
                            minZ = Math.min(minZ, p[2]);
                            maxZ = Math.max(maxZ, p[2]);
                            it.remove();
                            expanded = true;
                        }
                    }
                } while (expanded);

                int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                if (volume > 2) {
                    optimized.add("fill " + minX + " " + minY + " " + minZ + " "
                            + maxX + " " + maxY + " " + maxZ + " " + material + " replace");
                    optimizedCount++;
                } else {
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                optimized.add("setblock " + x + " " + y + " " + z + " " + material);
                            }
                        }
                    }
                    optimizedCount++;
                }
            }
        }

        result.addProperty("outputCount", optimized.size());
        result.addProperty("fillCommands", optimizedCount);
        result.add("optimizedCommands", optimized);
        return result.toString();
    }
}
