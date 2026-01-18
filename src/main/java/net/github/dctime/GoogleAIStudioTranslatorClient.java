package net.github.dctime;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = GoogleAIStudioTranslatorClient.MODID)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GoogleAIStudioTranslatorClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleAIStudioTranslatorClient.class);
    public static final String MODID = "googleaistudiotranslator";
    public GoogleAIStudioTranslatorClient() {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
//
//    public GoogleAIStudioTranslatorClient(IEventBus eventBus) {
//
//    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) throws IOException, InterruptedException {
        // Some client setup code
        KeyMapping.registerBindings();
    }


}
