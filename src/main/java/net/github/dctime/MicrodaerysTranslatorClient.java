package net.github.dctime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.github.dctime.libs.OfficialTranslationLookup;
import net.github.dctime.libs.Translator;
import net.github.dctime.screen.TranslatorConfigScreen;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MicrodaerysTranslatorClient.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MicrodaerysTranslatorClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrodaerysTranslatorClient.class);
    public static final String MODID = "microdaerystranslator";
    public MicrodaerysTranslatorClient(ModContainer container) {
        // Custom config screen (TranslatorConfigScreen) instead of NeoForge's generic
        // ConfigurationScreen -- see mailbox #002: the auto-generated screen exposes raw config
        // keys and free-text fields (endpoint/model_name/target_language/...) that aren't usable
        // for a player who doesn't know what an API endpoint or a language code is.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new TranslatorConfigScreen(parent));
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) throws IOException, InterruptedException {
        Translator.loadCacheFromDisk();
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Resource packs (a translation patch pack is a common one) can be added/removed/reordered
        // mid-session; without this, OfficialTranslationLookup would keep answering from whatever
        // snapshot it first loaded, silently ignoring anything the player adds afterward.
        event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        resourceManager -> OfficialTranslationLookup.invalidateCache());
    }


}
