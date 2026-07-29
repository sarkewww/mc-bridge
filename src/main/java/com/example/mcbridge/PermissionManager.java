package com.example.mcbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.*;

public class PermissionManager {
    private static boolean enabled = false;
    private static final Set<String> whitelist = new HashSet<>();

    static {
        PermissionConfig cfg = PermissionConfig.load();
        enabled = cfg.isEnabled();
        whitelist.clear();
        for (String cmd : cfg.getWhitelist()) {
            whitelist.add(cmd);
        }
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setEnabled(boolean v) {
        enabled = v;
        save();
    }

    public static synchronized Set<String> getWhitelistedCommands() {
        return Collections.unmodifiableSet(whitelist);
    }

    public static synchronized boolean isAllowed(String commandType) {
        if (!enabled) return true;
        return whitelist.contains(commandType.toLowerCase());
    }

    public static synchronized void addCommand(String cmd) {
        whitelist.add(cmd.toLowerCase());
        save();
    }

    public static synchronized void removeCommand(String cmd) {
        whitelist.remove(cmd.toLowerCase());
        save();
    }

    public static synchronized void clear() {
        whitelist.clear();
        save();
    }

    public static synchronized void showStatus(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) return;
        player.sendMessage(Text.literal("§7[Bridge] §6Permission System"));
        player.sendMessage(Text.literal(" §7Enabled: " + (enabled ? "§aYes" : "§cNo")));
        if (enabled) {
            player.sendMessage(Text.literal(" §7Whitelist (" + whitelist.size() + "):"));
            for (String cmd : whitelist) {
                player.sendMessage(Text.literal("  §7- §b" + cmd));
            }
            player.sendMessage(Text.literal(" §7Use §b!!whitelist add/remove <cmd>"));
        }
        player.sendMessage(Text.literal(" §7Use §b!!whitelist on/off §7to toggle"));
    }

    public static synchronized void load() {
        PermissionConfig cfg = PermissionConfig.load();
        enabled = cfg.isEnabled();
        whitelist.clear();
        for (String cmd : cfg.getWhitelist()) {
            whitelist.add(cmd);
        }
    }

    public static synchronized void save() {
        PermissionConfig cfg = PermissionConfig.getInstance();
        cfg.setEnabled(enabled);
        cfg.setWhitelist(whitelist);
        cfg.save();
    }
}
