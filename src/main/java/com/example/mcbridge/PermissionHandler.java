package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class PermissionHandler {

    public static String handlePermission(JsonObject json) {
        JsonObject result = new JsonObject();
        boolean needsSave = false;

        if (json.has("action")) {
            String action = json.get("action").getAsString();
            switch (action) {
                case "enable" -> {
                    PermissionManager.setEnabled(true);
                    needsSave = true;
                    result.addProperty("enabled", true);
                }
                case "disable" -> {
                    PermissionManager.setEnabled(false);
                    needsSave = true;
                    result.addProperty("enabled", false);
                }
                case "status" -> {
                    result.addProperty("enabled", PermissionManager.isEnabled());
                    JsonArray list = new JsonArray();
                    for (String cmd : PermissionManager.getWhitelistedCommands()) {
                        list.add(cmd);
                    }
                    result.add("whitelistedCommands", list);
                }
                case "add" -> {
                    if (!json.has("command")) throw new RuntimeException("Missing 'command' field");
                    String cmd = json.get("command").getAsString().toLowerCase();
                    PermissionManager.addCommand(cmd);
                    needsSave = true;
                    result.addProperty("added", cmd);
                    result.addProperty("whitelistSize", PermissionManager.getWhitelistedCommands().size());
                }
                case "remove" -> {
                    if (!json.has("command")) throw new RuntimeException("Missing 'command' field");
                    String cmd = json.get("command").getAsString().toLowerCase();
                    PermissionManager.removeCommand(cmd);
                    needsSave = true;
                    result.addProperty("removed", cmd);
                }
                case "clear" -> {
                    PermissionManager.clear();
                    needsSave = true;
                    result.addProperty("cleared", true);
                }
                default -> result.addProperty("error", "Unknown action: " + action);
            }
        } else {
            result.addProperty("enabled", PermissionManager.isEnabled());
            result.addProperty("whitelistSize", PermissionManager.getWhitelistedCommands().size());
        }

        if (needsSave) {
            try {
                PermissionManager.save();
            } catch (Exception e) {
                result.addProperty("saveError", e.getMessage());
            }
        }

        return result.toString();
    }
}
