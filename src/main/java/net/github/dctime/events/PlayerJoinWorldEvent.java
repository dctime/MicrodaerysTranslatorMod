package net.github.dctime.events;

import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT)
public class PlayerJoinWorldEvent {
    private static boolean loginHandled = false;

    @SubscribeEvent
    public static void onLocalPlayerJoinLevel(EntityJoinWorldEvent event) {
        if (!loginHandled && event.getEntity() == Minecraft.getInstance().player) {
            loginHandled = true;
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("感謝使用 Google AI Studio Translator! 自動翻譯提示匡, Jade, Advancements 與 FTBQuest 的內容的小工具!").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("螢幕截圖會把裡面的英文文字翻成中文顯示在對話框").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("使用前請先去修改 config/googleaistudiotranslator-client.toml").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("按F4可以清除翻譯快取 可去按鍵設定修改").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("在GUI裡按Left Alt可以顯示最近翻譯的螢幕截圖 可去按鍵設定修改").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("如果找到bug或是想要什麼請").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            Minecraft.getInstance().player.displayClientMessage(new TextComponent("點這裡").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/dctime/GoogleAIStudioTranslatorMod/issues"))), false);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        loginHandled = false;
    }
}
