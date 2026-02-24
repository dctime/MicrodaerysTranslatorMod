package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.KeyMapping;
import net.github.dctime.libs.Translator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.client.event.ClientTickEvent;

@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class OnClientTickEvent {
    @SubscribeEvent // on the game event bus only on the physical client
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        while (KeyMapping.DELETE_TRANSLATION_CACHE.get().consumeClick()) {
            Translator.clearCache();
        }

        if (KeyMapping.DELETE_SHOWING_TRANSLATION.get().isDown()) {
            Translator.setDeletingTranslationKeyHold(true, Translator.KeyTriggeredSource.CLIENT_TICK);
//            System.out.println("Client Tick TRUE");
        } else {
            Translator.setDeletingTranslationKeyHold(false, Translator.KeyTriggeredSource.CLIENT_TICK);
//            System.out.println("Client Tick FALSE");
        }
    }
}
