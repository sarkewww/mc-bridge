package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;

public class SocialHandler {

    public static String handleFindVillager(MinecraftClient client) {
        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "World not loaded");
            return err.toString();
        }

        var player = client.player;
        if (player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Not in game");
            return err.toString();
        }

        var playerPos = player.getPos();
        JsonArray villagers = new JsonArray();
        double nearestDist = Double.MAX_VALUE;
        JsonObject nearest = null;

        for (var entity : world.getEntities()) {
            if (entity.getType().toString().contains("villager")) {
                JsonObject v = new JsonObject();
                v.addProperty("id", entity.getId());
                v.addProperty("name", entity.getName().getString());
                v.addProperty("uuid", entity.getUuid().toString());
                var pos = entity.getPos();
                v.addProperty("x", round(pos.x, 1));
                v.addProperty("y", round(pos.y, 1));
                v.addProperty("z", round(pos.z, 1));
                double dist = pos.distanceTo(playerPos);
                v.addProperty("distance", round(dist, 1));

                villagers.add(v);

                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = v;
                }
            }
        }

        result.addProperty("count", villagers.size());
        result.add("villagers", villagers);
        if (nearest != null) {
            result.add("nearest", nearest);
        }
        return result.toString();
    }

    public static String handleGetPlayerProfile(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : "";
        JsonObject result = new JsonObject();
        if (name.isEmpty()) {
            name = PlayerProfileManager.getSelfName();
        }

        String profileJson;
        if (json.has("server") && !json.get("server").getAsString().isEmpty()) {
            profileJson = PlayerProfileManager.getRawProfileForServer(name, json.get("server").getAsString());
        } else {
            profileJson = PlayerProfileManager.getRawProfile(name);
        }

        if (profileJson != null && !profileJson.isEmpty()) {
            result.add("profile", JsonParser.parseString(profileJson));
            result.addProperty("found", true);
        } else {
            result.add("profile", JsonNull.INSTANCE);
            result.addProperty("found", false);
        }

        return result.toString();
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
