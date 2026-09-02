package net.github.dctime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.github.dctime.libs.OfficialTranslationLookup;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.provider.ProviderAdapterRegistry;
import net.github.dctime.libs.provider.ProviderInfo;
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
        checkProviderRegistriesAreComplete();
    }

    /**
     * A provider has to appear in FOUR separate places to actually work: {@link Config.EndPoint}
     * itself, {@link Config#PROVIDER_KEYS} (or the separate CUSTOM_PROVIDER_* fields for Custom),
     * {@link ProviderInfo#ALL}, and {@link ProviderAdapterRegistry} (see mailbox review round 016,
     * point M1) -- each with a DIFFERENT failure mode if one is forgotten (a loud exception from
     * the adapter registry or {@code ProviderInfo.of}, a silent NPE from
     * {@code Config.PROVIDER_KEYS.get(...)} on the translation path). Checking once here, at
     * startup, turns "forgot to register a provider somewhere" into one clear log line at mod load
     * instead of a confusing crash the first time a player hovers an item with that provider
     * selected. Deliberately logs and continues rather than throwing: this environment's exact
     * behavior on an exception from an {@code FMLClientSetupEvent} handler (does the whole game
     * refuse to start, or does it degrade to a mod-loading-error screen) wasn't verified, and this
     * mod's own established rule is "worst case is a warning, never a crash" -- better to log
     * loudly and let the specific broken provider fail later than risk the entire game not
     * starting over what is, for every player except this mod's own developer, an impossible state.
     */
    private static void checkProviderRegistriesAreComplete() {
        for (Config.EndPoint endpoint : Config.EndPoint.values()) {
            try {
                ProviderAdapterRegistry.forEndpoint(endpoint);
            } catch (Exception e) {
                LOGGER.error("No TranslationProviderAdapter registered for " + endpoint + " -- this provider cannot translate.");
            }
            try {
                ProviderInfo.of(endpoint);
            } catch (Exception e) {
                LOGGER.error("No ProviderInfo registered for " + endpoint + " -- this provider cannot appear correctly in the config GUI.");
            }
            if (endpoint != Config.EndPoint.CUSTOM && !Config.PROVIDER_KEYS.containsKey(endpoint)) {
                LOGGER.error("No Config.PROVIDER_KEYS entry for " + endpoint + " -- this provider's settings cannot be saved or loaded.");
            }
        }
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
