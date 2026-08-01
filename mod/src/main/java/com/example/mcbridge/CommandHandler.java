package com.example.mcbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CommandHandler {

    private static final Gson GSON = new Gson();

    public static String handle(String type, JsonObject json, MinecraftClient client) throws Exception {
        ClientPlayerEntity player = client.player;

        // Server/analysis commands that don't need a player entity
        switch (type) {
            case "config_reload" -> { return WorldHandler.handleConfigReload(); }
            case "get_server_info" -> { return handleGetServerInfo(); }
            case "set_server_brand" -> { return handleSetServerBrand(json); }
            case "get_all_servers" -> { return handleGetAllServers(); }
            case "get_pending_analysis" -> { return handleGetPendingAnalysis(); }
            case "mark_analysis_done" -> { return handleMarkAnalysisDone(json); }
            case "travel_log_stats" -> { return WorldHandler.handleTravelLogStats(); }
            case "waypoint_export" -> { return WorldHandler.handleWaypointExport(json); }
        }

        if (player == null) {
            throw new Exception("Not in game");
        }

        try {
            return switch (type) {
                case "ping" -> "pong";
                case "exec" -> handleExec(json, player);
                case "pos" -> PlayerHandler.handlePos(player);
                case "f3" -> PlayerHandler.handleF3(client);
                case "chat" -> ChatHandler.handleChat(json, player);
                case "entities" -> EntityBlockHandler.handleEntities(json, client);
                case "inv" -> PlayerHandler.handleInventory(player);
                case "info" -> WorldHandler.handleInfo(client, player);
                case "lookat" -> handleLookAt(json, player);
                case "mods" -> handleMods();
                case "baritone" -> AutomationHandler.handleBaritone();
                case "wurst" -> AutomationHandler.handleWurst();
                case "chatlog" -> ChatHandler.handleChatlog(json);
                case "container" -> GUIHandler.handleContainer(client, player);
                case "entity" -> EntityBlockHandler.handleEntity(json, client);
                case "entity_detail" -> EntityBlockHandler.handleEntityDetail(json, client);
                case "block" -> EntityBlockHandler.handleBlock(json, client, player);
                case "intercept" -> ChatHandler.handleIntercept(json);
                case "logs" -> DebugHandler.handleLogs(json, client);
                case "highlight" -> EntityBlockHandler.handleHighlight(json, client);
                case "send" -> ChatHandler.handleSend(json, player);
                case "screen" -> GUIHandler.handleScreen(client, player);
                case "screenshot" -> AutomationHandler.handleScreenshot(client);
                case "permission" -> PermissionHandler.handlePermission(json);
                case "run_script" -> AutomationHandler.handleRunScript(json, client, player);
                case "gui_click" -> GUIHandler.handleGuiClick(json, client, player);
                case "trade" -> GUIHandler.handleTrade(json, client, player);
                case "mirror" -> AutomationHandler.handleMirror(json, client);
                case "find_blocks" -> EntityBlockHandler.handleFindBlocks(json, client);
                case "find_item" -> EntityBlockHandler.handleFindItem(json, player);
                case "player_effects" -> PlayerHandler.handlePlayerEffects(player);
                case "statistics" -> PlayerHandler.handleStatistics(player);
                case "sign" -> PlayerHandler.handleSign(json, client, player);
                case "world_border" -> WorldHandler.handleWorldBorder(client);
                case "player_abilities" -> PlayerHandler.handlePlayerAbilities(player);
                case "last_death" -> PlayerHandler.handleLastDeath(player);
                case "press_key" -> AutomationHandler.handlePressKey(json, client, player);
                case "use_item" -> AutomationHandler.handleUseItem(client);
                case "walk_to" -> AutomationHandler.handleWalkTo(json, client, player);
                case "chunk" -> EntityBlockHandler.handleChunk(json, client);
                case "biome" -> EntityBlockHandler.handleBiome(json, client);
                case "break_block" -> EntityBlockHandler.handleBreakBlock(json, client, player);
                case "place_block" -> EntityBlockHandler.handlePlaceBlock(json, client, player);
                case "seed" -> WorldHandler.handleSeed(client);
                case "summon" -> EntityBlockHandler.handleSummon(json, player);
                case "move_item" -> GUIHandler.handleMoveItem(json, client, player);
                case "drop_item" -> GUIHandler.handleDropItem(json, client, player);
                case "equip_item" -> GUIHandler.handleEquipItem(json, client, player);
                case "scoreboard" -> WorldHandler.handleScoreboard(client);
                case "bossbar" -> WorldHandler.handleBossBar(client);
                case "advancements" -> WorldHandler.handleAdvancements(client);
                case "weather" -> WorldHandler.handleWeather(client);
                case "gamemode" -> PlayerHandler.handleGameMode(client);
                case "xp" -> PlayerHandler.handleXp(player);
                case "time" -> WorldHandler.handleTime(client);
                case "hotbar_select" -> PlayerHandler.handleHotbarSelect(json, player);
                case "recipes" -> WorldHandler.handleRecipes(client, player);
                case "light_level" -> EntityBlockHandler.handleLightLevel(json, client, player);
                case "clear_chat" -> ChatHandler.handleClearChat(client);
                case "locate_structure" -> WorldHandler.handleLocateStructure(json, player);
                case "locate_biome" -> WorldHandler.handleLocateBiome(json, player);
                case "item_detail" -> handleItemDetail(json, player);
                case "recipe_for_item" -> WorldHandler.handleRecipeForItem(json, client, player);
                case "nearby_players" -> PlayerHandler.handleNearbyPlayers(client);
                case "direction" -> PlayerHandler.handleDirection(player);
                case "auto_fish" -> AutomationHandler.handleAutoFish(json, client);
                case "screenshot_repeat" -> AutomationHandler.handleScreenshotRepeat(json, client);
                case "find_villager" -> SocialHandler.handleFindVillager(client);
                case "sort_inventory" -> GUIHandler.handleSortInventory(client, player);
                case "refill" -> GUIHandler.handleRefill(json, client, player);
                case "craft_item" -> GUIHandler.handleCraftItem(json, client, player);
                case "scan_terrain" -> EntityBlockHandler.handleScanTerrain(json, client, player);
                case "explain_screen" -> GUIHandler.handleExplainScreen(json, client, player);
                case "analyze_inventory" -> GUIHandler.handleAnalyzeInventory(client, player);
                case "batch_build" -> AutomationHandler.handleBatchBuild(json, client, player);
                case "entity_highlight" -> EntityBlockHandler.handleEntityHighlight(json, client);
                case "damage_display" -> EntityBlockHandler.handleDamageDisplay(json, client);
                case "tps" -> PlayerHandler.handleTps(client);
                case "reach" -> PlayerHandler.handleReach(client, player);
                case "ping_info" -> PlayerHandler.handlePingInfo(client);
                case "packet_logger" -> DebugHandler.handlePacketLogger(json);
                case "packet_logger_detail" -> DebugHandler.handlePacketLoggerDetail(json);
                case "packet_logger_find" -> DebugHandler.handlePacketLoggerFind(json);
                case "bedrock_breaker" -> AutomationHandler.handleBedrockBreaker(json, client, player);
                case "scan_containers" -> EntityBlockHandler.handleScanContainers(json, client);
                case "shulker_peek" -> EntityBlockHandler.handleShulkerPeek(json, player);
                case "waypoints" -> handleWaypoints(json, client, player);
                case "travel_log" -> handleTravelLog(json, client, player);
                case "scan_crops" -> EntityBlockHandler.handleScanCrops(json, client, player);
                case "block_counter" -> EntityBlockHandler.handleBlockCounter(json, client);
                case "get_player_profile" -> SocialHandler.handleGetPlayerProfile(json);
                case "auto_explore" -> AutomationHandler.handleAutoExplore(json);
                case "memory_add" -> MemoryHandler.handleMemoryAdd(json);
                case "memory_recall" -> MemoryHandler.handleMemoryRecall(json);
                case "memory_list" -> MemoryHandler.handleMemoryList(json);
                case "memory_delete" -> MemoryHandler.handleMemoryDelete(json);
                case "memory_summary" -> MemoryHandler.handleMemorySummary();
                case "memory_near" -> MemoryHandler.handleMemoryNear(json);
                case "find_spawners" -> WorldHandler.handleFindSpawners(json, client);
                case "is_slime_chunk" -> WorldHandler.handleIsSlimeChunk(json);
                case "attack_entity" -> EntityBlockHandler.handleAttackEntity(json, client);
                case "interact_entity" -> EntityBlockHandler.handleInteractEntity(json, client);
                case "ride_entity" -> EntityBlockHandler.handleRideEntity(json, client);
                case "memory_export" -> MemoryHandler.handleMemoryExport(json);
                case "waypoint_import" -> WorldHandler.handleWaypointImport(json, client);
                case "read_comparator" -> EntityBlockHandler.handleReadComparator(json, client);
                case "toggle_block" -> EntityBlockHandler.handleToggleBlock(json, client, player);
                case "spawn_particle" -> WorldHandler.handleSpawnParticle(json, client);
                case "play_sound" -> WorldHandler.handlePlaySound(json, client);
                case "auto_tnt" -> EntityBlockHandler.handleAutoTnt(json, client, player);
                case "ice_boat_navigate" -> AutomationHandler.handleIceBoatNavigate(json);
                case "ice_boat_stop" -> AutomationHandler.handleIceBoatStop();
                case "ice_boat_status" -> AutomationHandler.handleIceBoatStatus();
                case "afk_standin" -> handleAfkStandin(json, client);
                default -> throw new Exception("未知命令: " + type);
            };
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = "未知错误";
            throw new Exception(msg);
        }
    }

    private static String handleExec(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("cmd")) throw new Exception("Missing 'cmd' parameter");
        String cmd = json.get("cmd").getAsString();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        player.networkHandler.sendChatCommand(cmd);
        return "ok: /" + cmd;
    }

    private static String handleLookAt(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z")) {
            throw new Exception("Missing x, y, or z parameter");
        }
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();

        Vec3d target = new Vec3d(x, y, z);
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));

        player.setYaw(yaw);
        player.setPitch(pitch);

        JsonObject result = new JsonObject();
        result.addProperty("yaw", round(yaw, 1));
        result.addProperty("pitch", round(pitch, 1));
        result.addProperty("target", String.format("%.1f, %.1f, %.1f", x, y, z));
        return result.toString();
    }

    private static String handleMods() {
        JsonObject result = new JsonObject();

        JsonArray modList = new JsonArray();
        FabricLoader.getInstance().getAllMods().stream()
                .sorted(Comparator.comparing(m -> m.getMetadata().getId()))
                .forEach(mod -> {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", mod.getMetadata().getId());
                    entry.addProperty("name", mod.getMetadata().getName());
                    entry.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                    modList.add(entry);
                });
        result.add("mods", modList);
        result.addProperty("count", modList.size());

        return result.toString();
    }

    private static String handleItemDetail(JsonObject json, ClientPlayerEntity player) throws Exception {
        ItemStack stack;
        if (json.has("slot")) {
            int slot = json.get("slot").getAsInt();
            if (slot >= 0 && slot < 36) {
                stack = player.getInventory().main.get(slot);
            } else if (slot == 40) {
                stack = player.getOffHandStack();
            } else if (slot >= 100 && slot < 104) {
                stack = player.getInventory().armor.get(slot - 100);
            } else {
                throw new Exception("Invalid slot: " + slot);
            }
        } else {
            stack = player.getMainHandStack();
        }
        if (stack.isEmpty()) {
            return "{\"item\":null,\"empty\":true}";
        }
        return itemStackToJson(stack, null).toString();
    }

    private static JsonObject itemStackToJson(ItemStack stack, EquipmentSlot slot) {
        JsonObject obj = new JsonObject();
        obj.addProperty("slot", slot != null ? slot.getName() : "inventory");
        obj.addProperty("item", stack.getItem().toString());
        obj.addProperty("count", stack.getCount());
        obj.addProperty("maxCount", stack.getMaxCount());
        obj.addProperty("displayName", stack.getName().getString());
        obj.addProperty("durability", stack.getMaxDamage() - stack.getDamage());
        obj.addProperty("maxDurability", stack.getMaxDamage());
        obj.addProperty("damage", stack.getDamage());
        obj.addProperty("hasCustomName", stack.get(DataComponentTypes.CUSTOM_NAME) != null);

        ItemEnchantmentsComponent ench = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            JsonArray enchList = new JsonArray();
            for (var entry : ench.getEnchantments()) {
                JsonObject e = new JsonObject();
                e.addProperty("id", entry.getIdAsString());
                e.addProperty("level", ench.getLevel(entry));
                enchList.add(e);
            }
            obj.add("enchantments", enchList);
        }

        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            JsonArray loreArr = new JsonArray();
            for (var line : lore.lines()) {
                loreArr.add(line.getString());
            }
            obj.add("lore", loreArr);
        }

        return obj;
    }

    // --- afk_standin ---
    private static String handleAfkStandin(JsonObject json, MinecraftClient client) throws Exception {
        String action = json.has("action") ? json.get("action").getAsString() : "status";
        return switch (action) {
            case "enable" -> { AFKStandinService.enable(); yield "{\"ok\":true}"; }
            case "disable" -> { AFKStandinService.disable(); yield "{\"ok\":true}"; }
            case "status" -> AFKStandinService.handleStatus();
            case "learn" -> AFKStandinService.handleLearn(json, client);
            case "mark_replied" -> {
                if (!json.has("messageId")) throw new Exception("Missing messageId");
                AFKStandinService.markReplied(json.get("messageId").getAsString());
                yield "{\"ok\":true}";
            }
            default -> throw new Exception("Unknown afk_standin action: " + action);
        };
    }

    // --- handleWaypoints ---
    private static String handleWaypoints(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        String action = json.has("action") ? json.get("action").getAsString() : "list";
        JsonObject result = new JsonObject();

        switch (action) {
            case "add" -> {
                String name = json.has("name") ? json.get("name").getAsString() : "";
                double x = json.has("x") ? json.get("x").getAsDouble() : player.getX();
                double y = json.has("y") ? json.get("y").getAsDouble() : player.getY();
                double z = json.has("z") ? json.get("z").getAsDouble() : player.getZ();
                if (name.isEmpty()) return errorJson("Missing 'name' for waypoint");
                WaypointManager.add(name, x, y, z, player.getWorld().getRegistryKey().getValue().toString());
                result.addProperty("added", name);
            }
            case "remove" -> {
                String name = json.has("name") ? json.get("name").getAsString() : "";
                if (name.isEmpty()) return errorJson("Missing 'name'");
                WaypointManager.remove(name);
                result.addProperty("removed", name);
            }
            case "list" -> {
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
            }
            case "goto" -> {
                String name = json.has("name") ? json.get("name").getAsString() : "";
                if (name.isEmpty()) return errorJson("Missing 'name'");
                var wp = WaypointManager.get(name);
                if (wp == null) return errorJson("Waypoint not found: " + name);
                result.addProperty("x", wp.x());
                result.addProperty("y", wp.y());
                result.addProperty("z", wp.z());
                result.addProperty("dimension", wp.dimension());
                result.addProperty("action", "goto");
                result.addProperty("suggestion", "Use mc_walk_to(" + wp.x() + "," + wp.y() + "," + wp.z() + ") to navigate");
            }
            default -> result.addProperty("error", "Unknown action: " + action);
        }

        return result.toString();
    }

    // --- handleTravelLog ---
    private static String handleTravelLog(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        String action = json.has("action") ? json.get("action").getAsString() : "get";
        JsonObject result = new JsonObject();

        switch (action) {
            case "start" -> {
                TravelLogService.start();
                result.addProperty("status", "recording");
                result.addProperty("info", "Travel logging started (30s interval)");
            }
            case "stop" -> {
                TravelLogService.stop();
                result.addProperty("status", "stopped");
            }
            case "get" -> {
                int limit = json.has("limit") ? json.get("limit").getAsInt() : 100;
                JsonArray points = new JsonArray();
                var log = TravelLogService.getRecent(limit);
                for (var p : log) {
                    JsonObject pt = new JsonObject();
                    pt.addProperty("x", round(p.x(), 1));
                    pt.addProperty("y", round(p.y(), 1));
                    pt.addProperty("z", round(p.z(), 1));
                    pt.addProperty("dimension", p.dimension());
                    pt.addProperty("time", p.timestamp());
                    points.add(pt);
                }
                result.addProperty("count", points.size());
                result.add("points", points);
            }
            default -> result.addProperty("error", "Unknown action: " + action);
        }

        return result.toString();
    }

    // --- get_server_info ---
    private static String handleGetServerInfo() {
        return ServerContext.getCurrentServerInfoJson();
    }

    // --- set_server_brand ---
    private static String handleSetServerBrand(JsonObject json) {
        if (!json.has("address") || !json.has("brand") || !json.has("networkType") || !json.has("displayName"))
            return errorJson("Missing required fields: address, brand, networkType, displayName");
        String address = json.get("address").getAsString();
        String brand = json.get("brand").getAsString();
        String networkType = json.get("networkType").getAsString();
        String displayName = json.get("displayName").getAsString();
        int maxPlayers = json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : 0;
        ServerContext.setServerInfo(address, brand, networkType, displayName, maxPlayers);
        ServerContext.refreshCurrentBrand();
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("brand", brand);
        return result.toString();
    }

    // --- get_all_servers ---
    private static String handleGetAllServers() {
        return ServerContext.getAllServersJson();
    }

    // --- get_pending_analysis ---
    private static String handleGetPendingAnalysis() {
        return PlayerProfileManager.getPendingAnalysisProfilesJson();
    }

    // --- mark_analysis_done ---
    private static String handleMarkAnalysisDone(JsonObject json) {
        if (!json.has("name")) return errorJson("Missing 'name' parameter");
        String name = json.get("name").getAsString();
        PlayerProfileManager.markAnalysisDone(name);
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        return result.toString();
    }

    static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }
}
