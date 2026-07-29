package com.example.mcbridge.mixin;

import com.example.mcbridge.ChatLog;
import com.example.mcbridge.Config;
import com.example.mcbridge.HelpI18n;
import com.example.mcbridge.InterceptState;
import com.example.mcbridge.PermissionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
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
}
