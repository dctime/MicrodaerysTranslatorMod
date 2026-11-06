package net.github.dctime.events;

import net.github.dctime.Config;
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


@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class OnClientTickEvent {
    // 20 ticks/sec, so this is ~30 seconds between disk-cache flush checks.
    private static final int FLUSH_CHECK_INTERVAL_TICKS = 600;
    private static int ticksSinceLastFlushCheck = 0;

    @SubscribeEvent // on the game event bus only on the physical client
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Forge 1.20.1 fires ClientTickEvent twice per tick (START + END); NeoForge's
        // ClientTickEvent.Post is END only. Without this filter FLUSH_CHECK_INTERVAL_TICKS would
        // silently halve and container pre-translation would run twice per tick.
        if (event.phase != TickEvent.Phase.END) return;
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

        if (++ticksSinceLastFlushCheck >= FLUSH_CHECK_INTERVAL_TICKS) {
            ticksSinceLastFlushCheck = 0;
            Translator.flushCacheToDiskIfDirty();
        }

        if (Config.ENABLE_PRETRANSLATE_CONTAINERS.get()) {
            Translator.pretranslateOpenContainerIfAny();
        }
    }
}
