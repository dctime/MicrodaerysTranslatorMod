package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class PlayerJoinWorldEvent {
    private static boolean loginHandled = false;

    @SubscribeEvent
    public static void onLocalPlayerJoinLevel(EntityJoinLevelEvent event) {
        if (!loginHandled && event.getEntity() == Minecraft.getInstance().player) {
            loginHandled = true;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("感謝使用 Microdaery's Translator! 自動翻譯提示匡, Jade, Advancements 與 FTBQuest 的內容的小工具!").withStyle(net.minecraft.ChatFormatting.GREEN));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("螢幕截圖會把裡面的英文文字翻成中文顯示在對話框").withStyle(net.minecraft.ChatFormatting.GREEN));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("使用前請先去修改 config/microdaerystranslator-client.toml").withStyle(net.minecraft.ChatFormatting.GREEN));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("按F4可以清除翻譯快取 按F6可以重新翻譯正在顯示的翻譯 可去按鍵設定修改").withStyle(net.minecraft.ChatFormatting.GREEN));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("在GUI裡按Left Alt可以顯示最近翻譯的螢幕截圖 可去按鍵設定修改").withStyle(net.minecraft.ChatFormatting.GREEN));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("如果找到bug或是想要什麼請").withStyle(net.minecraft.ChatFormatting.GREEN));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("點這裡").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/dctime/MicrodaerysTranslatorMod/issues"))));
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        loginHandled = false;
    }
}
