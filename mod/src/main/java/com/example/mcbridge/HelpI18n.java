package com.example.mcbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class HelpI18n {

    public static Text get() {
        String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage().toLowerCase();

        if (lang.startsWith("ja")) return jp();
        if (lang.startsWith("en")) return en();
        if (lang.startsWith("zh")) return zh();
        return en();
    }

    public static Text zh() {
        return Text.literal("§7[Bridge] 命令列表:\n" +
                "§b!!intercept§7 - 查看拦截状态\n" +
                "§b!!intercept on/off§7 - 开启/关闭\n" +
                "§b!!intercept c/n§7 - copy/拦截模式\n" +
                "§b!!iceboat start x=<X> z=<Z>§7 - 冰船导航\n" +
                "§b!!iceboat stop/status§7 - 停止/状态\n" +
                "§b!!help§7 - 本帮助");
    }

    public static Text en() {
        return Text.literal("§7[Bridge] Commands:\n" +
                "§b!!intercept§7 - show intercept status\n" +
                "§b!!intercept on/off§7 - toggle\n" +
                "§b!!intercept c/n§7 - copy/intercept mode\n" +
                "§b!!iceboat start x=<X> z=<Z>§7 - ice boat nav\n" +
                "§b!!iceboat stop/status§7 - stop/status\n" +
                "§b!!help§7 - this help");
    }

    public static Text jp() {
        return Text.literal("§7[Bridge] コマンド一覧:\n" +
                "§b!!intercept§7 - インターセプト状態表示\n" +
                "§b!!intercept on/off§7 - 切替\n" +
                "§b!!intercept c/n§7 - copy/インターセプト モード\n" +
                "§b!!iceboat start x=<X> z=<Z>§7 - 氷ボートナビ\n" +
                "§b!!iceboat stop/status§7 - 停止/状態\n" +
                "§b!!help§7 - ヘルプ");
    }
}
