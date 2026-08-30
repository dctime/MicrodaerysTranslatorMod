package net.github.dctime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.github.dctime.libs.OfficialTranslationLookup;
import net.github.dctime.libs.Translator;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
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
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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
