package com.example.mcbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PermissionConfig {
    private static final Path CONFIG_DIR = Paths.get("", "config", "mc-bridge");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("mc-bridge-permissions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean enabled = false;
    private Set<String> whitelist = new HashSet<>();

    private static PermissionConfig instance = new PermissionConfig();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; save(); }

    public Set<String> getWhitelist() { return Collections.unmodifiableSet(whitelist); }

    public void addCommand(String cmd) { whitelist.add(cmd.toLowerCase()); save(); }
    public void removeCommand(String cmd) { whitelist.remove(cmd.toLowerCase()); save(); }
    public void clearWhitelist() { whitelist.clear(); save(); }

    public void setWhitelist(Collection<String> cmds) { this.whitelist = new HashSet<>(cmds); }

    public boolean isAllowed(String commandType) {
        if (!enabled) return true;
        return whitelist.contains(commandType.toLowerCase());
    }

    public static PermissionConfig getInstance() { return instance; }

    public static PermissionConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = new Gson().fromJson(json, PermissionConfig.class);
                if (instance == null) instance = new PermissionConfig();
                if (instance.whitelist == null) instance.whitelist = new HashSet<>();
            }
        } catch (Exception e) {
            McBridgeMod.LOGGER.warn("[mc-bridge] Failed to load permission config", e);
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (Exception e) {
            McBridgeMod.LOGGER.warn("[mc-bridge] Failed to save permission config", e);
        }
    }
}
