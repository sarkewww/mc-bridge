package com.example.mcbridge.mixin;

import com.example.mcbridge.ChatLog;
import com.example.mcbridge.HelpI18n;
import com.example.mcbridge.IceController;
import com.example.mcbridge.InterceptState;
import com.example.mcbridge.PermissionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatInterceptorMixin {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (InterceptState.isBypassing()) return;

        if (message.startsWith("!!")) {
            ci.cancel();
            String cmd = message.substring(2).trim().toLowerCase();
            MinecraftClient client = MinecraftClient.getInstance();

            switch (cmd) {
                case "intercept" -> {
                    // Show status, don't change mode
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §eCurrent mode: "
                                + (InterceptState.getMode() == InterceptState.Mode.OFF ? "§cOFF"
                                    : InterceptState.getMode() == InterceptState.Mode.COPY ? "§eCOPY (in c)"
                                    : "§eBLOCK (in n)")
                                + "§r  Use §b!!in c §r(copy mode) or §b!!in n §r(block mode)"));
                }
                case "intercept on" -> {
                    InterceptState.setMode(InterceptState.Mode.INTERCEPT);
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §aIntercept ON (block + record)"));
                }
                case "intercept off" -> {
                    InterceptState.setMode(InterceptState.Mode.OFF);
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §cIntercept OFF"));
                }
                case "in c", "intercept copy", "intercept c" -> {
                    InterceptState.setMode(InterceptState.Mode.COPY);
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §eCopy mode: send + record"));
                }
                case "in n", "intercept block", "intercept n" -> {
                    InterceptState.setMode(InterceptState.Mode.INTERCEPT);
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §eBlock mode: intercept + record"));
                }
                case "in" -> {
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §eCurrent mode: "
                                + (InterceptState.getMode() == InterceptState.Mode.OFF ? "§cOFF"
                                    : InterceptState.getMode() == InterceptState.Mode.COPY ? "§eCOPY (in c)"
                                    : "§eBLOCK (in n)")
                                + "§r  Use §b!!in c §r(copy mode) or §b!!in n §r(block mode)"));
                }
                case "help" -> {
                    if (client.player != null)
                        client.player.sendMessage(HelpI18n.get());
                }
                case "whitelist" -> {
                    PermissionManager.showStatus(client.player);
                }
                case "whitelist on", "whitelist enable" -> {
                    PermissionManager.setEnabled(true);
                    PermissionManager.save();
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §aPermission whitelist ON"));
                }
                case "whitelist off", "whitelist disable" -> {
                    PermissionManager.setEnabled(false);
                    PermissionManager.save();
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §cPermission whitelist OFF"));
                }
                case String s when s.startsWith("whitelist add ") -> {
                    String wlCmd = s.substring(14).trim().toLowerCase();
                    PermissionManager.addCommand(wlCmd);
                    PermissionManager.save();
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §aAdded '" + wlCmd + "' to whitelist"));
                }
                case String s when s.startsWith("whitelist remove ") -> {
                    String wlCmd = s.substring(17).trim().toLowerCase();
                    PermissionManager.removeCommand(wlCmd);
                    PermissionManager.save();
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §aRemoved '" + wlCmd + "' from whitelist"));
                }
                case "iceboat" -> {
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §b!!iceboat start x=<X> z=<Z> [scan_radius] §7| §b!!iceboat stop §7| §b!!iceboat status"));
                }
                case String s when s.startsWith("iceboat start ") -> {
                    String args = s.substring(14).trim();
                    handleIceboatStart(args, client);
                }
                case "iceboat stop" -> {
                    String result = IceController.stop();
                    if (client.player != null) {
                        try {
                            var obj = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
                            String msg = obj.has("ok") && obj.get("ok").getAsBoolean()
                                    ? "§a" + obj.get("message").getAsString()
                                    : "§c" + obj.get("error").getAsString();
                            client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] " + msg));
                        } catch (Exception e) {
                            client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §e" + result));
                        }
                    }
                }
                case "iceboat status" -> {
                    String result = IceController.status();
                    if (client.player != null) {
                        try {
                            var obj = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
                            StringBuilder sb = new StringBuilder("§7[Bridge] §b冰船状态:\n");
                            sb.append("§7运行: ").append(obj.has("running") && obj.get("running").getAsBoolean() ? "§a是" : "§c否").append("\n");
                            if (obj.has("target")) sb.append("§7目标: §e").append(obj.get("target").getAsString()).append("\n");
                            if (obj.has("distance_to_target")) sb.append("§7剩余: §e").append(obj.get("distance_to_target").getAsString()).append("m\n");
                            if (obj.has("waypoints")) sb.append("§7路点: §e").append(obj.get("waypoints").getAsInt()).append(" 当前#").append(obj.get("waypoint_index").getAsInt()).append("\n");
                            if (obj.has("last_action")) sb.append("§7操作: §e").append(obj.get("last_action").getAsString()).append("\n");
                            if (obj.has("graph_nodes")) sb.append("§7图谱节点: §e").append(obj.get("graph_nodes").getAsInt());
                            client.player.sendMessage(net.minecraft.text.Text.literal(sb.toString()));
                        } catch (Exception e) {
                            client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §e" + result));
                        }
                    }
                }
                default -> {
                    if (client.player != null)
                        client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §eUnknown !! command. Try §b!!help"));
                }
            }

            ChatLog.add("[!!CMD] " + cmd);
            return;
        }

        if (!InterceptState.isEnabled()) return;
        if (message.startsWith("/")) return;

        MinecraftClient client = MinecraftClient.getInstance();

        if (InterceptState.isCopyMode()) {
            // COPY mode: send directly + record (no blocking)
            ChatLog.addRaw(message, client.player != null ? client.player.getName().getString() : "Self", "copy");
            // Message goes through normally (we only record)
            return;
        }

        // INTERCEPT mode: block + record
        ci.cancel();
        if (client.player != null) {
            ChatLog.addRaw(message, client.player.getName().getString(), "intercepted");
            InterceptState.runBypass(() ->
                    client.player.networkHandler.sendChatMessage(message));
        } else {
            ChatLog.addRaw(message, "Self", "intercepted");
        }
    }

    private void handleIceboatStart(String args, MinecraftClient client) {
        double x = 0, z = 0;
        int scanRadius = 8;
        boolean setX = false, setZ = false;
        for (String part : args.split("\\s+")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                if (t.startsWith("x=")) { x = Double.parseDouble(t.substring(2)); setX = true; }
                else if (t.startsWith("z=")) { z = Double.parseDouble(t.substring(2)); setZ = true; }
                else if (!t.contains("=")) {
                    double v = Double.parseDouble(t);
                    if (!setX) { x = v; setX = true; }
                    else if (!setZ) { z = v; setZ = true; }
                    else scanRadius = (int) v;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (!setX || !setZ) {
            if (client.player != null)
                client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §c用法: !!iceboat start x=<X> z=<Z> [scan_radius]"));
            return;
        }
        String result = IceController.start(x, z, scanRadius);
        if (client.player != null) {
            try {
                var obj = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
                String msg;
                if (obj.has("ok") && obj.get("ok").getAsBoolean()) {
                    msg = "§a" + obj.get("message").getAsString() + " (§7" + obj.get("graph_nodes").getAsInt() + "节点, 半径" + obj.get("scan_radius").getAsInt() + "§a)";
                } else {
                    msg = "§c" + obj.get("error").getAsString();
                }
                client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] " + msg));
            } catch (Exception e) {
                client.player.sendMessage(net.minecraft.text.Text.literal("§7[Bridge] §e" + result));
            }
        }
    }
}
