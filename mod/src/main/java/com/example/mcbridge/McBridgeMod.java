package com.example.mcbridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McBridgeMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("mc-bridge");
    static WebSocketBridge bridge;
    private static String lastServerAddress = null;

    @Override
    public void onInitializeClient() {
        Config.load();
        AutoConfig.load();
        LOGGER.info("[mc-bridge] Config loaded. Intercept={}",
                InterceptState.isEnabled());

        LOGGER.info("[mc-bridge] Starting WebSocket bridge on port 25575");
        bridge = new WebSocketBridge(25575);
        bridge.start();

        PlayerTrackerService.start();

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String text = message.getString();
            String senderName = sender != null ? sender.getName() : null;
            String brand = ServerContext.getCurrentBrand();
            if (InterceptState.isEnabled()) {
                ChatLog.addRaw(text, senderName, "to_me");
            } else {
                ChatLog.add(text);
            }
            AFKStandinService.onIncomingChat(senderName != null ? senderName : "?", text);
        });

        HighlightRenderer.register();
        IceBoatHud.register();

        ClientTickEvents.START_CLIENT_TICK.register(IceController::onClientTick);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            ChatLog.add("[actionbar] " + message.getString());
        });

        // AFK activity tracking
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerHandler.onClientTick();

            if (AFKStandinService.isEnabled()) {
                var player = client.player;
                if (player != null) {
                    boolean moving = player.horizontalSpeed > 0.01f || player.getVelocity().lengthSquared() > 0.001;
                    if (moving || client.mouse.wasRightButtonClicked() || client.mouse.wasLeftButtonClicked()) {
                        AFKStandinService.updateActivity();
                    }
                }
            }
        });

        // Server change detection
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            String currentAddr = null;
            if (client.getCurrentServerEntry() != null) {
                currentAddr = client.getCurrentServerEntry().address;
            }
            if (currentAddr != null && !currentAddr.equals(lastServerAddress)) {
                String previousBrand = ServerContext.getCurrentBrand();
                if (previousBrand != null && !previousBrand.isEmpty()) {
                    for (String name : PlayerTrackerService.getOnlineNames()) {
                        PlayerProfileManager.recordOffline(name, previousBrand);
                    }
                }
                PlayerProfileManager.saveAll();
                lastServerAddress = currentAddr;
                ServerContext.refreshCurrentBrand();
                if (ServerContext.needsAnalysis(currentAddr)) {
                    String motd = client.getCurrentServerEntry().label != null
                            ? client.getCurrentServerEntry().label.getString() : "";
                    LOGGER.info("[mc-bridge] New server detected: {} (MOTD: {}). Waiting for MCP analysis.",
                            currentAddr, motd);
                } else {
                    LOGGER.info("[mc-bridge] Server: {} → brand: {}",
                            currentAddr, ServerContext.getBrand(currentAddr));
                }
            }
        });

        // Save all profiles on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PlayerTrackerService.stop();
            PlayerProfileManager.saveAll();
        }));
    }
}
