package net.github.dctime.events;


import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.datagen.LanguageProvider;
import net.github.dctime.datagen.LanguageProviderDeDe;
import net.github.dctime.datagen.LanguageProviderEsEs;
import net.github.dctime.datagen.LanguageProviderFrFr;
import net.github.dctime.datagen.LanguageProviderJaJp;
import net.github.dctime.datagen.LanguageProviderKoKr;
import net.github.dctime.datagen.LanguageProviderPtBr;
import net.github.dctime.datagen.LanguageProviderRuRu;
import net.github.dctime.datagen.LanguageProviderZhCn;
import net.github.dctime.datagen.LanguageProviderZhTw;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GatherDataEvent {
    @SubscribeEvent
    public static void onGatherData(net.minecraftforge.forge.event.lifecycle.GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        // Register the providers -- one per locale (en_us is also Minecraft's fallback locale for
        // any key a non-en_us file doesn't have; see LanguageProvider's javadoc).
        // 1.18.2's DataGenerator.addProvider has no includeClient() flag -- this mod only ever
        // generates client assets (lang files), so every provider is unconditional.
        generator.addProvider(new LanguageProvider(generator));
        generator.addProvider(new LanguageProviderZhTw(generator));
        generator.addProvider(new LanguageProviderZhCn(generator));
        generator.addProvider(new LanguageProviderJaJp(generator));
        generator.addProvider(new LanguageProviderEsEs(generator));
        generator.addProvider(new LanguageProviderFrFr(generator));
        generator.addProvider(new LanguageProviderKoKr(generator));
        generator.addProvider(new LanguageProviderRuRu(generator));
        generator.addProvider(new LanguageProviderDeDe(generator));
        generator.addProvider(new LanguageProviderPtBr(generator));
    }
}
