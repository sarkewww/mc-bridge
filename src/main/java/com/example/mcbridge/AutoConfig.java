package com.example.mcbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;

public class AutoConfig {
    private static final Path CONFIG_DIR = Paths.get("", "config", "mc-bridge");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("mc-bridge-auto.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int autoFishTimeout = 60000;
    private int autoExploreInterval = 1000;
    private int reconnectDelay = 3000;
    private int reconnectMaxAttempts = 20;

    private static AutoConfig instance = new AutoConfig();

    public int getAutoFishTimeout() { return autoFishTimeout; }
    public void setAutoFishTimeout(int v) { this.autoFishTimeout = v; save(); }

    public int getAutoExploreInterval() { return autoExploreInterval; }
    public void setAutoExploreInterval(int v) { this.autoExploreInterval = v; save(); }

    public int getReconnectDelay() { return reconnectDelay; }
    public void setReconnectDelay(int v) { this.reconnectDelay = v; save(); }

    public int getReconnectMaxAttempts() { return reconnectMaxAttempts; }
    public void setReconnectMaxAttempts(int v) { this.reconnectMaxAttempts = v; save(); }

    public static AutoConfig getInstance() { return instance; }

    public static AutoConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = new Gson().fromJson(json, AutoConfig.class);
                if (instance == null) instance = new AutoConfig();
                if (instance.autoFishTimeout == 0) instance.autoFishTimeout = 60000;
                if (instance.autoExploreInterval == 0) instance.autoExploreInterval = 1000;
                if (instance.reconnectDelay == 0) instance.reconnectDelay = 3000;
                if (instance.reconnectMaxAttempts == 0) instance.reconnectMaxAttempts = 20;
            }
        } catch (Exception e) {
            McBridgeMod.LOGGER.warn("[mc-bridge] Failed to load auto config", e);
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (Exception e) {
            McBridgeMod.LOGGER.warn("[mc-bridge] Failed to save auto config", e);
        }
    }
}
