package com.example.mcbridge.mixin;

import com.example.mcbridge.ChatLog;
import com.example.mcbridge.InterceptState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void onAddMessageSigned(Text message, MessageSignatureData signature, net.minecraft.client.gui.hud.MessageIndicator indicator, CallbackInfo ci) {
        String raw = message.getString();
        if (raw.contains("[Bridge]") || raw.contains("[RAW]")) return;

        if (InterceptState.isEnabled()) {
            ChatLog.add("[RAW] " + raw);
        }
    }
}
