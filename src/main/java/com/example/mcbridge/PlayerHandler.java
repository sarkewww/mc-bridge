package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class PlayerHandler {

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }

    public static String handlePos(ClientPlayerEntity player) {
        Vec3d pos = player.getPos();
        JsonObject result = new JsonObject();
        result.addProperty("x", round(pos.x, 2));
        result.addProperty("y", round(pos.y, 2));
        result.addProperty("z", round(pos.z, 2));
        result.addProperty("yaw", round(player.getYaw(), 1));
        result.addProperty("pitch", round(player.getPitch(), 1));
        result.addProperty("onGround", player.isOnGround());
        result.addProperty("dimension", player.getWorld().getRegistryKey().getValue().toString());
        return result.toString();
    }

    public static String handleF3(MinecraftClient client) {
        JsonObject info = new JsonObject();

        info.addProperty("fps", client.getCurrentFps());

        ClientPlayerEntity player = client.player;
        if (player != null) {
            Vec3d pos = player.getPos();
            info.addProperty("x", round(pos.x, 2));
            info.addProperty("y", round(pos.y, 2));
            info.addProperty("z", round(pos.z, 2));
            info.addProperty("yaw", round(player.getYaw(), 1));
            info.addProperty("pitch", round(player.getPitch(), 1));
            info.addProperty("health", round(player.getHealth(), 1));
            info.addProperty("maxHealth", round(player.getMaxHealth(), 1));
            info.addProperty("food", player.getHungerManager().getFoodLevel());
            info.addProperty("saturation", round(player.getHungerManager().getSaturationLevel(), 1));
            info.addProperty("armor", player.getArmor());
            info.addProperty("xpLevel", player.experienceLevel);
            info.addProperty("xpProgress", round(player.experienceProgress, 3));
            info.addProperty("dimension", player.getWorld().getRegistryKey().getValue().toString());

            var gm = client.interactionManager.getCurrentGameMode();
            info.addProperty("gameMode", gm != null ? gm.getName() : "unknown");
        }

        var world = client.world;
        if (world != null) {
            long entityCount = 0;
            int playerCount = 0;
            for (var e : world.getEntities()) {
                entityCount++;
                if (e instanceof PlayerEntity) playerCount++;
            }
            info.addProperty("entities", entityCount);
            info.addProperty("players", playerCount);
        }

        Runtime rt = Runtime.getRuntime();
        info.addProperty("memoryUsed", (rt.totalMemory() - rt.freeMemory()) / 1048576 + "MB");
        info.addProperty("memoryMax", rt.maxMemory() / 1048576 + "MB");

        if (client.getNetworkHandler() != null) {
            info.addProperty("ping", client.getNetworkHandler().getPlayerListEntry(player.getUuid()) != null
                    ? client.getNetworkHandler().getPlayerListEntry(player.getUuid()).getLatency()
                    : -1);
        }

        return info.toString();
    }

    public static String handleInventory(ClientPlayerEntity player) {
        JsonObject result = new JsonObject();

        JsonArray tools = new JsonArray();
        JsonArray weapons = new JsonArray();
        JsonArray armor = new JsonArray();
        JsonArray food = new JsonArray();
        JsonArray blocks = new JsonArray();
        JsonArray building = new JsonArray();
        JsonArray potions = new JsonArray();
        JsonArray other = new JsonArray();

        var mainInv = player.getInventory().main;
        int slot = 0;
        int totalSlots = 36;
        for (int i = 0; i < totalSlots; i++) {
            var stack = mainInv.get(i);
            if (!stack.isEmpty()) {
                JsonObject item = new JsonObject();
                item.addProperty("slot", slot);
                item.addProperty("name", stack.getItem().toString());
                item.addProperty("count", stack.getCount());
                item.addProperty("maxCount", stack.getMaxCount());
                item.addProperty("displayName", stack.getName().getString());

                String id = stack.getItem().toString();
                boolean categorized = false;
                if (isTool(id)) {
                    tools.add(item); categorized = true;
                } else if (id.contains("sword") || id.contains("bow") || id.contains("crossbow") || id.contains("trident") || id.contains("arrow") || id.contains("snowball") || id.contains("egg") || id.contains("ender_pearl")) {
                    weapons.add(item); categorized = true;
                } else if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots") || id.contains("elytra") || id.contains("shield") || id.contains("turtle")) {
                    armor.add(item); categorized = true;
                } else if (isFood(id)) {
                    food.add(item); categorized = true;
                } else if (isRawBlock(id)) {
                    blocks.add(item); categorized = true;
                } else if (id.contains("potion") || id.contains("lingering") || id.contains("splash") || id.contains("glass_bottle")) {
                    potions.add(item); categorized = true;
                } else if (id.contains("composter") || id.contains("chest") || id.contains("barrel") || id.contains("furnace") || id.contains("crafting") || id.contains("torch") || id.contains("ladder") || id.contains("door") || id.contains("trapdoor") || id.contains("fence") || id.contains("gate") || id.contains("slab") || id.contains("stair") || id.contains("lever") || id.contains("button") || id.contains("pressure") || id.contains("redstone") || id.contains("repeater") || id.contains("comparator") || id.contains("piston") || id.contains("observer") || id.contains("dispenser") || id.contains("dropper") || id.contains("hopper") || id.contains("rail")) {
                    building.add(item); categorized = true;
                } else {
                    other.add(item);
                }
            }
            slot++;
        }

        result.add("tools", tools);
        result.add("weapons", weapons);
        result.add("armor", armor);
        result.add("food", food);
        result.add("blocks", blocks);
        result.add("building", building);
        result.add("potions", potions);
        result.add("other", other);

        var offhand = player.getOffHandStack();
        if (!offhand.isEmpty()) {
            result.addProperty("offhand", offhand.getItem().toString());
        }

        JsonArray armorSlots = new JsonArray();
        var armorInv = player.getInventory().armor;
        for (int i = 0; i < armorInv.size(); i++) {
            var stack = armorInv.get(i);
            if (!stack.isEmpty()) {
                JsonObject item = new JsonObject();
                item.addProperty("slot", i);
                item.addProperty("name", stack.getItem().toString());
                item.addProperty("count", stack.getCount());
                armorSlots.add(item);
            }
        }
        if (armorSlots.size() > 0) {
            result.add("armorSlots", armorSlots);
        }

        result.addProperty("selectedSlot", player.getInventory().selectedSlot);
        var held = player.getMainHandStack();
        if (!held.isEmpty()) {
            result.addProperty("heldItem", held.getItem().toString());
        }

        return result.toString();
    }

    public static String handleDirection(ClientPlayerEntity player) {
        float yaw = player.getYaw();
        if (yaw < 0) yaw += 360;
        String dir;
        if (yaw < 22.5 || yaw >= 337.5) dir = "S";
        else if (yaw < 67.5) dir = "SW";
        else if (yaw < 112.5) dir = "W";
        else if (yaw < 157.5) dir = "NW";
        else if (yaw < 202.5) dir = "N";
        else if (yaw < 247.5) dir = "NE";
        else if (yaw < 292.5) dir = "E";
        else dir = "SE";
        JsonObject r = new JsonObject();
        r.addProperty("yaw", player.getYaw());
        r.addProperty("direction", dir);
        return r.toString();
    }

    public static String handlePlayerEffects(ClientPlayerEntity player) {
        JsonArray effects = new JsonArray();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            JsonObject ef = new JsonObject();
            ef.addProperty("id", effect.getEffectType().getIdAsString());
            ef.addProperty("translationKey", effect.getTranslationKey());
            ef.addProperty("amplifier", effect.getAmplifier());
            ef.addProperty("amplifierDisplay", effect.getAmplifier() + 1);
            ef.addProperty("duration", effect.getDuration());
            ef.addProperty("durationSeconds", effect.getDuration() / 20);
            ef.addProperty("ambient", effect.isAmbient());
            ef.addProperty("showParticles", effect.shouldShowParticles());
            ef.addProperty("showIcon", effect.shouldShowIcon());
            effects.add(ef);
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", effects.size());
        result.add("effects", effects);
        return result.toString();
    }

    public static String handleStatistics(ClientPlayerEntity player) {
        StatHandler sh = player.getStatHandler();
        JsonObject r = new JsonObject();
        var custom = Stats.CUSTOM;
        r.addProperty("walk_cm", sh.getStat(custom.getOrCreateStat(Stats.WALK_ONE_CM)));
        r.addProperty("crouch_cm", sh.getStat(custom.getOrCreateStat(Stats.CROUCH_ONE_CM)));
        r.addProperty("sprint_cm", sh.getStat(custom.getOrCreateStat(Stats.SPRINT_ONE_CM)));
        r.addProperty("swim_cm", sh.getStat(custom.getOrCreateStat(Stats.SWIM_ONE_CM)));
        r.addProperty("fall_cm", sh.getStat(custom.getOrCreateStat(Stats.FALL_ONE_CM)));
        r.addProperty("fly_cm", sh.getStat(custom.getOrCreateStat(Stats.FLY_ONE_CM)));
        r.addProperty("boat_cm", sh.getStat(custom.getOrCreateStat(Stats.BOAT_ONE_CM)));
        r.addProperty("aviate_cm", sh.getStat(custom.getOrCreateStat(Stats.AVIATE_ONE_CM)));
        r.addProperty("jump", sh.getStat(custom.getOrCreateStat(Stats.JUMP)));
        r.addProperty("drop", sh.getStat(custom.getOrCreateStat(Stats.DROP)));
        r.addProperty("damage_dealt", sh.getStat(custom.getOrCreateStat(Stats.DAMAGE_DEALT)));
        r.addProperty("damage_taken", sh.getStat(custom.getOrCreateStat(Stats.DAMAGE_TAKEN)));
        r.addProperty("deaths", sh.getStat(custom.getOrCreateStat(Stats.DEATHS)));
        r.addProperty("mob_kills", sh.getStat(custom.getOrCreateStat(Stats.MOB_KILLS)));
        r.addProperty("player_kills", sh.getStat(custom.getOrCreateStat(Stats.PLAYER_KILLS)));
        r.addProperty("animals_bred", sh.getStat(custom.getOrCreateStat(Stats.ANIMALS_BRED)));
        r.addProperty("fish_caught", sh.getStat(custom.getOrCreateStat(Stats.FISH_CAUGHT)));
        r.addProperty("enchant_item", sh.getStat(custom.getOrCreateStat(Stats.ENCHANT_ITEM)));
        r.addProperty("play_time", sh.getStat(custom.getOrCreateStat(Stats.PLAY_TIME)));
        r.addProperty("talked_to_villager", sh.getStat(custom.getOrCreateStat(Stats.TALKED_TO_VILLAGER)));
        r.addProperty("traded_with_villager", sh.getStat(custom.getOrCreateStat(Stats.TRADED_WITH_VILLAGER)));
        r.addProperty("open_chest", sh.getStat(custom.getOrCreateStat(Stats.OPEN_CHEST)));
        r.addProperty("open_enderchest", sh.getStat(custom.getOrCreateStat(Stats.OPEN_ENDERCHEST)));
        return r.toString();
    }

    public static String handlePlayerAbilities(ClientPlayerEntity player) {
        var a = player.getAbilities();
        JsonObject r = new JsonObject();
        r.addProperty("creative", a.creativeMode);
        r.addProperty("flying", a.flying);
        r.addProperty("allow_flying", a.allowFlying);
        r.addProperty("invulnerable", a.invulnerable);
        r.addProperty("walk_speed", a.getWalkSpeed());
        r.addProperty("fly_speed", a.getFlySpeed());
        return r.toString();
    }

    public static String handleXp(ClientPlayerEntity player) {
        JsonObject r = new JsonObject();
        r.addProperty("level", player.experienceLevel);
        r.addProperty("progress", player.experienceProgress);
        r.addProperty("total_experience", player.totalExperience);
        return r.toString();
    }

    public static String handleGameMode(MinecraftClient client) {
        return "{\"gamemode\":\"" + client.interactionManager.getCurrentGameMode().getName() + "\"}";
    }

    public static String handleLastDeath(ClientPlayerEntity player) {
        JsonObject r = new JsonObject();
        var deathPos = player.getLastDeathPos();
        if (deathPos.isPresent()) {
            var globalPos = deathPos.get();
            BlockPos pos = globalPos.pos();
            r.addProperty("dimension", globalPos.dimension().getValue().toString());
            r.addProperty("x", pos.getX());
            r.addProperty("y", pos.getY());
            r.addProperty("z", pos.getZ());
        } else {
            r.addProperty("has_death_pos", false);
        }
        return r.toString();
    }

    public static String handleHotbarSelect(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("slot")) throw new Exception("Missing 'slot' parameter");
        int slot = json.get("slot").getAsInt();
        if (slot < 0 || slot > 8) throw new Exception("Slot must be 0-8");
        player.getInventory().selectedSlot = slot;
        return "{\"selected_slot\":" + slot + "}";
    }

    public static String handleSign(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        BlockPos pos;
        if (json.has("x") && json.has("y") && json.has("z")) {
            pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        } else if (json.has("look")) {
            if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK)
                throw new Exception("Not looking at a block");
            pos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        } else {
            throw new Exception("Missing coordinates (x,y,z) or 'look' flag");
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign))
            throw new Exception("Not a sign at " + pos.toShortString());
        JsonObject r = new JsonObject();
        r.addProperty("x", pos.getX()); r.addProperty("y", pos.getY()); r.addProperty("z", pos.getZ());
        JsonArray front = new JsonArray();
        for (int i = 0; i < 4; i++) front.add(sign.getFrontText().getMessage(i, false).getString());
        r.add("front_text", front);
        JsonArray back = new JsonArray();
        for (int i = 0; i < 4; i++) back.add(sign.getBackText().getMessage(i, false).getString());
        r.add("back_text", back);
        r.addProperty("is_waxed", sign.isWaxed());
        return r.toString();
    }

    // --- mc_ping_info ---
    public static String handlePingInfo(MinecraftClient client) {
        JsonObject result = new JsonObject();
        var networkHandler = client.getNetworkHandler();
        if (networkHandler == null) return errorJson("Not connected to server");

        var playerList = networkHandler.getPlayerList();
        if (playerList == null) return errorJson("No player list");

        int totalPing = 0;
        int count = 0;
        int minPing = Integer.MAX_VALUE;
        int maxPing = 0;
        int selfPing = 0;
        String selfName = client.player != null ? client.player.getName().getString() : "";

        JsonArray players = new JsonArray();
        for (var entry : playerList) {
            int latency = entry.getLatency();
            String name = entry.getProfile().getName();
            totalPing += latency;
            count++;

            if (latency < minPing) minPing = latency;
            if (latency > maxPing) maxPing = latency;
            if (name.equals(selfName)) selfPing = latency;

            JsonObject p = new JsonObject();
            p.addProperty("name", name);
            p.addProperty("ping", latency);
            players.add(p);
        }

        result.addProperty("selfPing", selfPing);
        result.addProperty("avgPing", count > 0 ? round((double) totalPing / count, 0) : 0);
        result.addProperty("minPing", minPing == Integer.MAX_VALUE ? 0 : minPing);
        result.addProperty("maxPing", maxPing);
        result.addProperty("playerCount", count);
        result.add("players", players);

        return result.toString();
    }

    // --- mc_tps ---
    private static final TickTracker TICK_TRACKER = new TickTracker();

    public static String handleTps(MinecraftClient client) {
        JsonObject result = new JsonObject();
        int ticks = TICK_TRACKER.lastWindowTicks;
        long nanos = TICK_TRACKER.lastWindowNanos;
        if (ticks > 0 && nanos > 0) {
            double tps = Math.min(20.0, ticks * 1_000_000_000.0 / nanos);
            result.addProperty("tps", round(tps, 1));
            result.addProperty("msPerTick", round((double) nanos / 1_000_000.0 / ticks, 1));
        }
        result.addProperty("tickRate", client.getCurrentFps());
        result.addProperty("totalTicks", TICK_TRACKER.totalTicks);
        return result.toString();
    }

    public static void onClientTick() {
        long now = System.nanoTime();
        if (TICK_TRACKER.lastTickTime == 0) {
            TICK_TRACKER.lastTickTime = now;
            return;
        }
        TICK_TRACKER.tickCount++;
        TICK_TRACKER.totalTicks++;
        long elapsed = now - TICK_TRACKER.lastTickTime;
        if (elapsed >= 1_000_000_000L) {
            TICK_TRACKER.lastWindowTicks = TICK_TRACKER.tickCount;
            TICK_TRACKER.lastWindowNanos = elapsed;
            TICK_TRACKER.tickCount = 0;
            TICK_TRACKER.lastTickTime = now;
        }
    }

    private static class TickTracker {
        static long lastTickTime = 0;
        static int tickCount = 0;
        static int lastWindowTicks = 0;
        static long lastWindowNanos = 0;
        static long totalTicks = 0;
    }

    // --- mc_reach ---
    public static String handleReach(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        var hitResult = client.crosshairTarget;

        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            result.addProperty("reach", 0);
            result.addProperty("target", "none");
            return result.toString();
        }

        Vec3d eyePos = player.getEyePos();
        Vec3d playerPos = player.getPos();
        Vec3d hitPos = hitResult.getPos();
        double distance = hitPos.distanceTo(eyePos);

        result.addProperty("reach", round(distance, 2));
        result.addProperty("targetType", hitResult.getType().toString());

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hitResult;
            result.addProperty("blockX", bhr.getBlockPos().getX());
            result.addProperty("blockY", bhr.getBlockPos().getY());
            result.addProperty("blockZ", bhr.getBlockPos().getZ());
            result.addProperty("side", bhr.getSide().toString());
        } else if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult ehr = (EntityHitResult) hitResult;
            result.addProperty("entityId", ehr.getEntity().getId());
            result.addProperty("entityName", ehr.getEntity().getName().getString());
        }

        result.addProperty("playerX", round(playerPos.x, 2));
        result.addProperty("playerY", round(playerPos.y, 2));
        result.addProperty("playerZ", round(playerPos.z, 2));

        return result.toString();
    }

    public static String handleNearbyPlayers(MinecraftClient client) throws Exception {
        var nh = client.getNetworkHandler();
        if (nh == null) throw new Exception("Not connected to a server");
        var list = nh.getPlayerList();
        JsonArray players = new JsonArray();
        for (var entry : list) {
            JsonObject p = new JsonObject();
            var profile = entry.getProfile();
            p.addProperty("name", profile.getName());
            p.addProperty("uuid", profile.getId().toString());
            var gm = entry.getGameMode();
            if (gm != null) p.addProperty("game_mode", gm.getName());
            p.addProperty("ping", entry.getLatency());
            var dn = entry.getDisplayName();
            if (dn != null) p.addProperty("display_name", dn.getString());
            players.add(p);
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", players.size());
        result.add("players", players);
        return result.toString();
    }

    // --- Category helpers for handleInventory ---
    private static boolean isTool(String id) {
        return id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") || id.contains("hoe")
            || id.contains("fishing_rod") || id.contains("shears") || id.contains("flint");
    }

    private static boolean isFood(String id) {
        return id.contains("cooked") || id.contains("bread") || id.contains("apple") || id.contains("potato")
            || id.contains("carrot") || id.contains("beetroot") || id.contains("berries") || id.contains("pork")
            || id.contains("beef") || id.contains("chicken") || id.contains("rabbit") || id.contains("mutton")
            || id.contains("cod") || id.contains("salmon") || id.contains("cake") || id.contains("cookie")
            || id.contains("pumpkin_pie") || id.contains("suspicious_stew") || id.contains("honey_bottle")
            || id.contains("mushroom_stew") || id.contains("golden_") || id.contains("enchanted_golden");
    }

    private static boolean isRawBlock(String id) {
        if (!id.startsWith("minecraft:")) return false;
        return id.contains("_ore") || id.contains("_log") || id.contains("_planks") || id.contains("stone")
            || id.contains("cobble") || id.contains("dirt") || id.contains("sand") || id.contains("gravel")
            || id.contains("grass") || id.contains("wood") || id.contains("_brick") || id.contains("wool")
            || id.contains("glass") || id.contains("concrete") || id.contains("terracotta")
            || id.contains("netherrack") || id.contains("end_stone");
    }
}
