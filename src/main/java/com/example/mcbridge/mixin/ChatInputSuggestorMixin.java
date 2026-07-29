package com.example.mcbridge.mixin;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public class ChatInputSuggestorMixin {

    @Shadow @Final private TextFieldWidget textField;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public void clearWindow() {}
    @Shadow public void show(boolean narrateFirstSuggestion) {}

    @Unique
    private static final String[][] COMMANDS = {
        {"!!intercept", "Show intercept status"},
        {"!!intercept on", "Enable intercept (block mode)"},
        {"!!intercept off", "Disable intercept"},
        {"!!in", "Show intercept status (short)"},
        {"!!in c", "Copy mode: log + forward"},
        {"!!in n", "Block mode: intercept + forward"},
        {"!!help", "Show command help"},
        {"!!whitelist", "Show whitelist status"},
        {"!!whitelist on", "Enable command whitelist"},
        {"!!whitelist off", "Disable command whitelist"},
        {"!!whitelist add <cmd>", "Add command to whitelist"},
        {"!!whitelist remove <cmd>", "Remove command from whitelist"},
    };

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void onRefresh(CallbackInfo ci) {
        String text = textField.getText();
        if (!text.startsWith("!!")) return;

        ci.cancel();
        clearWindow();

        String prefix = text.toLowerCase();
        List<Suggestion> list = new ArrayList<>();
        for (String[] cmd : COMMANDS) {
            if (cmd[0].startsWith(prefix)) {
                list.add(new Suggestion(
                    new StringRange(0, text.length()),
                    cmd[0],
                    Text.literal(cmd[1])
                ));
            }
        }

        if (list.isEmpty()) return;

        pendingSuggestions = CompletableFuture.completedFuture(
            Suggestions.create(text, list));
        show(false);
    }
}
