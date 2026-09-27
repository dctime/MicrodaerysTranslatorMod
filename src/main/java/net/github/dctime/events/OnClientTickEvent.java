package net.github.dctime.events;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.KeyMapping;
import net.github.dctime.libs.Translator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class OnClientTickEvent {
    // 20 ticks/sec, so this is ~30 seconds between disk-cache flush checks.
    private static final int FLUSH_CHECK_INTERVAL_TICKS = 600;
    private static int ticksSinceLastFlushCheck = 0;

    @SubscribeEvent // on the game event bus only on the physical client
    public static void onClientTick(ClientTickEvent.Post event) {
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
