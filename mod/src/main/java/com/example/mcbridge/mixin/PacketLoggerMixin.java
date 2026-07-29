package com.example.mcbridge.mixin;

import com.example.mcbridge.PacketLoggerService;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class PacketLoggerMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!PacketLoggerService.isLogging() && !PacketLoggerService.isDetailLogging()) return;
        PacketLoggerService.recordPacket("C2S", packet.getClass().getSimpleName(), packet.toString());
    }
}
