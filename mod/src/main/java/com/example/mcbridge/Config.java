package com.example.mcbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {
    private static final Path PATH = Paths.get("config", "mc-bridge.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        if (!Files.exists(PATH)) return;
        try (Reader r = Files.newBufferedReader(PATH)) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            if (json.has("intercept_mode")) {
                String modeStr = json.get("intercept_mode").getAsString().toUpperCase();
                try {
                    InterceptState.setMode(InterceptState.Mode.valueOf(modeStr));
                } catch (IllegalArgumentException e) {
                    InterceptState.setMode(InterceptState.Mode.OFF);
                }
            } else if (json.has("intercept")) {
                InterceptState.setEnabled(json.get("intercept").getAsBoolean());
            }
        } catch (Exception e) {
            McBridgeMod.LOGGER.warn("[mc-bridge] Failed to load config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("intercept_mode", InterceptState.getMode().name().toLowerCase());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(json, w);
            }
        } catch (Exception e) {
            McBridgeMod.LOGGER.warn("[mc-bridge] Failed to save config", e);
        }
    }
}
