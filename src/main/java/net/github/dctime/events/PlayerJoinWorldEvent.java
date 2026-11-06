package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.WelcomeMessageTemplates;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class PlayerJoinWorldEvent {
    private static boolean loginHandled = false;

    @SubscribeEvent
    public static void onLocalPlayerJoinLevel(EntityJoinWorldEvent event) {
        if (!loginHandled && event.getEntity() == Minecraft.getInstance().player) {
            loginHandled = true;

            // matches whatever language Minecraft's own UI is currently showing the player --
            // NOT Translator's resolveTargetLanguage(), since this message explains how the mod
            // works and can differ from the AI translation target (follow_game_language=false).
            String currentGameLanguage = Minecraft.getInstance().getLanguageManager().getSelected().getCode();
            for (String line : WelcomeMessageTemplates.linesFor(currentGameLanguage)) {
                Minecraft.getInstance().player.displayClientMessage(new TextComponent(line).withStyle(net.minecraft.ChatFormatting.GREEN), false);
            }
            Minecraft.getInstance().player.displayClientMessage(new TextComponent(WelcomeMessageTemplates.clickHereFor(currentGameLanguage))
                    .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/dctime/MicrodaerysTranslatorMod/issues"))), false);
            Translator.warnIfTargetFollowsEnglishGameLanguage();
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        loginHandled = false;
        Translator.flushCacheToDiskSync(); // safety net on top of the periodic tick flush; sync, see its comment (E8)
    }
}
