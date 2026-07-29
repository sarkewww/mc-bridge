package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.advancement.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.*;
import net.minecraft.world.GameMode;
import net.minecraft.item.ItemStack;

import net.minecraft.block.BlockState;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.*;

public class WorldHandler {

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }

    public static String handleInfo(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject info = new JsonObject();

        var world = client.world;
        if (world != null) {
            info.addProperty("difficulty", world.getDifficulty().getName());
            info.addProperty("time", world.getTimeOfDay() % 24000);

            JsonArray playerList = new JsonArray();
            for (var p : world.getPlayers()) {
                JsonObject pObj = new JsonObject();
                pObj.addProperty("name", p.getName().getString());
                pObj.addProperty("uuid", p.getUuid().toString());
                var pp = p.getPos();
                pObj.addProperty("x", round(pp.x, 1));
                pObj.addProperty("y", round(pp.y, 1));
                pObj.addProperty("z", round(pp.z, 1));
                pObj.addProperty("distance", round(pp.distanceTo(player.getPos()), 1));
                playerList.add(pObj);
            }
            info.add("players", playerList);
        }

        if (client.getNetworkHandler() != null) {
            info.addProperty("serverBrand", client.getNetworkHandler().getBrand() != null
                    ? client.getNetworkHandler().getBrand()
                    : "vanilla");

            var playerList = client.getNetworkHandler().getPlayerList();
            if (playerList != null) {
                String selfName = client.player != null ? client.player.getName().getString() : "";
                for (var entry : playerList) {
                    if (entry.getProfile().getName().equals(selfName)) {
                        info.addProperty("selfPing", entry.getLatency());
                        break;
                    }
                }
            }
        }

        info.addProperty("clientVersion", client.getGameVersion());
        info.addProperty("sessionId", client.getSession().getSessionId() != null ? "valid" : "offline");

        return info.toString();
    }

    public static String handleWeather(MinecraftClient client) {
        var world = client.world;
        if (world == null) return "{}";
        JsonObject r = new JsonObject();
        r.addProperty("raining", world.isRaining());
        r.addProperty("thundering", world.isThundering());
        r.addProperty("rain_gradient", world.getRainGradient(1.0f));
        r.addProperty("thunder_gradient", world.getThunderGradient(1.0f));
        return r.toString();
    }

    public static String handleTime(MinecraftClient client) {
        var world = client.world;
        if (world == null) return "{}";
        long time = world.getTimeOfDay();
        JsonObject r = new JsonObject();
        r.addProperty("time_of_day", time);
        r.addProperty("day_time_24h", String.format("%02d:%02d", (time / 1000 + 6) % 24, (time % 1000) * 60 / 1000));
        r.addProperty("day", time / 24000L);
        return r.toString();
    }

    public static String handleSeed(MinecraftClient client) throws Exception {
        if (client.getServer() == null) throw new Exception("World seed only available in singleplayer (no integrated server)");
        long seed = client.getServer().getOverworld().getSeed();
        JsonObject result = new JsonObject();
        result.addProperty("seed", seed);
        result.addProperty("seedStr", Long.toString(seed));
        return result.toString();
    }

    public static String handleScoreboard(MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        var scoreboard = world.getScoreboard();
        JsonObject result = new JsonObject();
        JsonArray objectives = new JsonArray();
        for (var obj : scoreboard.getObjectives()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", obj.getName());
            o.addProperty("displayName", obj.getDisplayName().getString());
            o.addProperty("criterion", obj.getCriterion().getName());
            objectives.add(o);
        }
        result.add("objectives", objectives);
        result.addProperty("objectiveCount", objectives.size());

        JsonArray teams = new JsonArray();
        for (var team : scoreboard.getTeams()) {
            JsonObject t = new JsonObject();
            t.addProperty("name", team.getName());
            t.addProperty("displayName", team.getDisplayName().getString());
            t.addProperty("color", team.getColor().getName());
            teams.add(t);
        }
        result.add("teams", teams);
        return result.toString();
    }

    public static String handleBossBar(MinecraftClient client) throws Exception {
        JsonObject result = new JsonObject();
        var hud = client.inGameHud.getBossBarHud();
        if (hud == null) {
            result.addProperty("bossBars", 0);
            return result.toString();
        }
        var bars = bossBarsFromHud(hud);
        JsonArray barList = new JsonArray();
        for (var entry : bars.entrySet()) {
            var bar = entry.getValue();
            JsonObject b = new JsonObject();
            b.addProperty("uuid", entry.getKey().toString());
            b.addProperty("name", bar.getName().getString());
            b.addProperty("health", bar.getPercent());
            b.addProperty("color", bar.getColor().toString());
            b.addProperty("overlay", bar.getStyle().toString());
            b.addProperty("darkenSky", bar.shouldDarkenSky());
            b.addProperty("thickenFog", bar.shouldThickenFog());
            b.addProperty("dragonMusic", bar.hasDragonMusic());
            barList.add(b);
        }
        result.add("bossBars", barList);
        result.addProperty("count", barList.size());
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, net.minecraft.client.gui.hud.ClientBossBar> bossBarsFromHud(Object hud) {
        try {
            var field = hud.getClass().getDeclaredField("bossBars");
            field.setAccessible(true);
            return (Map<UUID, net.minecraft.client.gui.hud.ClientBossBar>) field.get(hud);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static String handleAdvancements(MinecraftClient client) throws Exception {
        JsonObject result = new JsonObject();
        var nh = client.getNetworkHandler();
        if (nh == null) throw new Exception("Not connected to server");
        var cam = nh.getAdvancementHandler();
        var manager = cam.getManager();
        var progresses = advancementProgressesMap(cam);
        JsonArray list = new JsonArray();
        for (var pa : manager.getAdvancements()) {
            JsonObject a = new JsonObject();
            var entry = pa.getAdvancementEntry();
            a.addProperty("id", entry.id().toString());
            var adv = pa.getAdvancement();
            var displayOpt = adv.display();
            if (displayOpt.isPresent()) {
                var display = displayOpt.get();
                a.addProperty("title", display.getTitle().getString());
                a.addProperty("description", display.getDescription().getString());
                a.addProperty("frame", display.getFrame().toString());
                a.addProperty("icon", display.getIcon().getItem().toString());
                var bg = display.getBackground();
                if (bg.isPresent()) a.addProperty("background", bg.get().toString());
                a.addProperty("toast", display.shouldShowToast());
                a.addProperty("announce", display.shouldAnnounceToChat());
                a.addProperty("hidden", display.isHidden());
            }
            var parentOpt = adv.parent();
            if (parentOpt.isPresent()) a.addProperty("parent", parentOpt.get().toString());
            a.addProperty("root", adv.isRoot());
            if (progresses.containsKey(entry)) {
                var prog = progresses.get(entry);
                JsonObject p = new JsonObject();
                p.addProperty("done", prog.isDone());
                p.addProperty("progress", prog.getProgressBarPercentage());
                var frac = prog.getProgressBarFraction();
                if (frac != null) p.addProperty("fraction", frac.getString());
                JsonArray doneList = new JsonArray();
                for (var c : prog.getObtainedCriteria()) doneList.add(c);
                p.add("doneCriteria", doneList);
                JsonArray undoneList = new JsonArray();
                for (var c : prog.getUnobtainedCriteria()) undoneList.add(c);
                p.add("undoneCriteria", undoneList);
                a.add("progress", p);
            }
            var parent = pa.getParent();
            if (parent != null) a.addProperty("parentId", parent.getAdvancementEntry().id().toString());
            a.addProperty("childrenCount", countChildren(pa));
            list.add(a);
        }
        result.add("advancements", list);
        result.addProperty("count", list.size());
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<AdvancementEntry, AdvancementProgress> advancementProgressesMap(Object cam) {
        try {
            var field = cam.getClass().getDeclaredField("advancementProgresses");
            field.setAccessible(true);
            return (Map<AdvancementEntry, AdvancementProgress>) field.get(cam);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static int countChildren(PlacedAdvancement pa) {
        int n = 0;
        for (var c : pa.getChildren()) n++;
        return n;
    }

    public static String handleWorldBorder(MinecraftClient client) throws Exception {
        if (client.world == null) throw new Exception("No world loaded");
        var border = client.world.getWorldBorder();
        JsonObject r = new JsonObject();
        r.addProperty("center_x", border.getCenterX());
        r.addProperty("center_z", border.getCenterZ());
        r.addProperty("size", border.getSize());
        r.addProperty("damage_per_block", border.getDamagePerBlock());
        r.addProperty("damage_buffer", border.getSafeZone());
        r.addProperty("warning_blocks", border.getWarningBlocks());
        r.addProperty("warning_time", border.getWarningTime());
        return r.toString();
    }

    public static String handleRecipes(MinecraftClient client, ClientPlayerEntity player) throws Exception {
        var rm = client.getNetworkHandler().getRecipeManager();
        var rb = player.getRecipeBook();
        JsonArray arr = new JsonArray();
        for (var entry : rm.values()) {
            JsonObject r = new JsonObject();
            r.addProperty("id", entry.id().toString());
            r.addProperty("type", entry.value().getType().toString());
            r.addProperty("unlocked", rb.contains(entry));
            r.addProperty("should_display", rb.shouldDisplay(entry));
            arr.add(r);
        }
        JsonObject result = new JsonObject();
        result.addProperty("total", arr.size());
        result.add("recipes", arr);
        return result.toString();
    }

    public static String handleRecipeForItem(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("item")) throw new Exception("Missing 'item' parameter");
        String query = json.get("item").getAsString().toLowerCase(Locale.ROOT);
        var rm = client.getNetworkHandler().getRecipeManager();
        var rb = player.getRecipeBook();
        JsonArray results = new JsonArray();
        for (var entry : rm.values()) {
            if (!rb.contains(entry)) continue;
            Recipe<?> recipe = entry.value();
            ItemStack output = recipe.getResult(client.world.getRegistryManager());
            if (output.isEmpty()) continue;
            String name = output.getItem().toString().toLowerCase(Locale.ROOT);
            String display = output.getName().getString().toLowerCase(Locale.ROOT);
            if (!name.contains(query) && !display.contains(query)) continue;
            JsonObject r = new JsonObject();
            r.addProperty("id", entry.id().toString());
            r.addProperty("type", recipe.getType().toString());
            r.addProperty("output_item", output.getItem().toString());
            r.addProperty("output_count", output.getCount());
            r.addProperty("output_name", output.getName().getString());
            JsonArray ingredients = new JsonArray();
            for (var ing : recipe.getIngredients()) {
                var matches = ing.getMatchingStacks();
                if (matches.length > 0) {
                    ingredients.add(matches[0].getItem().toString());
                }
            }
            r.add("ingredients", ingredients);
            results.add(r);
        }
        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("found", results.size());
        result.add("recipes", results);
        return result.toString();
    }

    public static String handleLocateStructure(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("structure")) throw new Exception("Missing 'structure' parameter");
        String s = json.get("structure").getAsString();
        player.networkHandler.sendChatCommand("locate structure " + s);
        return "{\"command\":\"/locate structure " + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    public static String handleLocateBiome(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("biome")) throw new Exception("Missing 'biome' parameter");
        String s = json.get("biome").getAsString();
        player.networkHandler.sendChatCommand("locate biome " + s);
        return "{\"command\":\"/locate biome " + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    // --- find_spawners ---
    public static String handleFindSpawners(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        int radius = json.has("radius") ? json.get("radius").getAsInt() : 32;
        BlockPos center = client.player.getBlockPos();

        int x1 = center.getX() - radius;
        int x2 = center.getX() + radius;
        int y1 = Math.max(world.getBottomY(), center.getY() - radius);
        int y2 = Math.min(world.getTopY(), center.getY() + radius);
        int z1 = center.getZ() - radius;
        int z2 = center.getZ() + radius;

        JsonArray spawners = new JsonArray();
        int checked = 0;

        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    checked++;
                    if (state.isAir()) continue;

                    if (state.getBlock() instanceof SpawnerBlock) {
                        JsonObject s = new JsonObject();
                        s.addProperty("x", x);
                        s.addProperty("y", y);
                        s.addProperty("z", z);
                        spawners.add(s);
                    }
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", spawners.size());
        result.addProperty("checked", checked);
        result.add("spawners", spawners);
        return result.toString();
    }

    // --- is_slime_chunk ---
    public static String handleIsSlimeChunk(JsonObject json) throws Exception {
        long seed = json.get("seed").getAsLong();
        int cx = json.get("cx").getAsInt();
        int cz = json.get("cz").getAsInt();
        boolean isSlime = isSlimeChunk(seed, cx, cz);
        int radius = json.has("radius") ? json.get("radius").getAsInt() : 0;

        JsonObject r = new JsonObject();
        r.addProperty("cx", cx);
        r.addProperty("cz", cz);
        r.addProperty("is_slime_chunk", isSlime);

        if (radius > 0) {
            JsonArray nearby = new JsonArray();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (isSlimeChunk(seed, cx + dx, cz + dz)) {
                        JsonObject p = new JsonObject();
                        p.addProperty("cx", cx + dx);
                        p.addProperty("cz", cz + dz);
                        nearby.add(p);
                    }
                }
            }
            r.addProperty("nearby_slime_chunks", nearby.size());
            r.add("slime_chunks", nearby);
        }

        return r.toString();
    }

    private static boolean isSlimeChunk(long seed, int cx, int cz) {
        Random rnd = new Random(
            seed +
            (long)(cx * cx * 0x4C1906) +
            (long)(cx * 0x5AC0DB) +
            (long)(cz * cz * 0x4307A7L) +
            (long)(cz * 0x5F24F) ^ 0x3AD8025F
        );
        return rnd.nextInt(10) == 0;
    }

    // --- travel_log_stats ---
    public static String handleTravelLogStats() throws Exception {
        var log = TravelLogService.getEntries();
        if (log == null || log.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("total_entries", 0);
            return empty.toString();
        }

        JsonObject stats = new JsonObject();
        stats.addProperty("total_entries", log.size());

        double totalDistance = 0;
        Set<String> dimensions = new HashSet<>();

        for (int i = 1; i < log.size(); i++) {
            var prev = log.get(i - 1);
            var cur = log.get(i);
            double dx = cur.x() - prev.x();
            double dz = cur.z() - prev.z();
            totalDistance += Math.sqrt(dx * dx + dz * dz);
            dimensions.add(cur.dimension());
        }

        if (!log.isEmpty()) {
            dimensions.add(log.get(0).dimension());
        }

        stats.addProperty("total_distance_blocks", Math.round(totalDistance));
        stats.addProperty("dimensions_visited", String.join(", ", dimensions));
        stats.addProperty("first_entry", log.get(0).timestamp());
        stats.addProperty("last_entry", log.get(log.size() - 1).timestamp());

        return stats.toString();
    }

    // --- waypoint_export ---
    public static String handleWaypointExport(JsonObject json) {
        JsonObject result = new JsonObject();
        JsonArray waypoints = new JsonArray();
        for (var wp : WaypointManager.getAll()) {
            JsonObject w = new JsonObject();
            w.addProperty("name", wp.name());
            w.addProperty("x", wp.x());
            w.addProperty("y", wp.y());
            w.addProperty("z", wp.z());
            w.addProperty("dimension", wp.dimension());
            waypoints.add(w);
        }
        result.addProperty("count", waypoints.size());
        result.add("waypoints", waypoints);
        return result.toString();
    }

    // --- waypoint_import ---
    public static String handleWaypointImport(JsonObject json, MinecraftClient client) {
        JsonObject result = new JsonObject();
        JsonArray list = json.has("waypoints") ? json.getAsJsonArray("waypoints") : new JsonArray();

        int added = 0;
        for (int i = 0; i < list.size(); i++) {
            JsonObject wp = list.get(i).getAsJsonObject();
            if (!wp.has("name") || !wp.has("x") || !wp.has("y") || !wp.has("z")) continue;
            String name = wp.get("name").getAsString();
            double x = wp.get("x").getAsDouble();
            double y = wp.get("y").getAsDouble();
            double z = wp.get("z").getAsDouble();
            String dim = wp.has("dimension") ? wp.get("dimension").getAsString() : "minecraft:overworld";
            WaypointManager.add(name, x, y, z, dim);
            added++;
        }

        result.addProperty("added", added);
        return result.toString();
    }

    // --- spawn_particle ---
    public static String handleSpawnParticle(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        if (!json.has("particle") || !json.has("x") || !json.has("y") || !json.has("z")) {
            throw new Exception("Missing particle, x, y, or z");
        }

        String particleTypeStr = json.get("particle").getAsString();
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        double vx = json.has("vx") ? json.get("vx").getAsDouble() : 0;
        double vy = json.has("vy") ? json.get("vy").getAsDouble() : 0.1;
        double vz = json.has("vz") ? json.get("vz").getAsDouble() : 0;
        int count = json.has("count") ? json.get("count").getAsInt() : 10;

        var particleType = Registries.PARTICLE_TYPE.get(Identifier.of(particleTypeStr));
        if (particleType == null) throw new Exception("Unknown particle: " + particleTypeStr);

        if (particleType instanceof ParticleEffect effect) {
            for (int i = 0; i < count; i++) {
                world.addParticle(effect, x, y, z, vx, vy, vz);
            }
        } else {
            throw new Exception("Particle type " + particleTypeStr + " requires additional parameters (use SimpleParticleType)");
        }

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("particle", particleTypeStr);
        result.addProperty("count", count);
        return result.toString();
    }

    // --- play_sound ---
    public static String handlePlaySound(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        var player = client.player;
        if (player == null) throw new Exception("No player");
        if (!json.has("sound")) throw new Exception("Missing sound parameter");

        String soundId = json.get("sound").getAsString();
        double x = json.has("x") ? json.get("x").getAsDouble() : player.getX();
        double y = json.has("y") ? json.get("y").getAsDouble() : player.getY();
        double z = json.has("z") ? json.get("z").getAsDouble() : player.getZ();
        float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0f;

        var soundEvent = Registries.SOUND_EVENT.get(Identifier.of(soundId));
        if (soundEvent == null) throw new Exception("Unknown sound: " + soundId);

        world.playSound(x, y, z, soundEvent, SoundCategory.MASTER, volume, pitch, false);

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("sound", soundId);
        return result.toString();
    }

    // --- config_reload ---
    public static String handleConfigReload() {
        Config.load();
        AutoConfig.load();
        PermissionConfig.load();
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "Config reloaded");
        return result.toString();
    }
}
