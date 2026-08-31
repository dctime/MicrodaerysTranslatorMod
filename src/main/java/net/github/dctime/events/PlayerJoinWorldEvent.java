package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.WelcomeMessageTemplates;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class PlayerJoinWorldEvent {
    private static boolean loginHandled = false;

    @SubscribeEvent
    public static void onLocalPlayerJoinLevel(EntityJoinLevelEvent event) {
        if (!loginHandled && event.getEntity() == Minecraft.getInstance().player) {
            loginHandled = true;

            // matches whatever language Minecraft's own UI is currently showing the player --
            // NOT Translator's resolveTargetLanguage(), since this message explains how the mod
            // works and can differ from the AI translation target (follow_game_language=false).
            String currentGameLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
            for (String line : WelcomeMessageTemplates.linesFor(currentGameLanguage)) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GREEN));
            }
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(WelcomeMessageTemplates.clickHereFor(currentGameLanguage))
                    .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/dctime/MicrodaerysTranslatorMod/issues"))));
        }
    }

    @SubscribeEvent
    public static void onClientLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        loginHandled = false;
        Translator.flushCacheToDiskIfDirty(); // safety net on top of the periodic tick flush
    }
}
