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
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class GatherDataEvent {
    @SubscribeEvent
    public static void onGatherData(net.neoforged.neoforge.data.event.GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // Register the providers -- one per locale (en_us is also Minecraft's fallback locale for
        // any key a non-en_us file doesn't have; see LanguageProvider's javadoc).
        generator.addProvider(
                // A boolean that determines whether the data should actually be generated.
                // The event provides methods that determine this:
                // event.includeClient(), event.includeServer(),
                // event.includeDev() and event.includeReports().
                // Since recipes are server data, we only run them in a server datagen.
                event.includeClient(),
                // Our provider.
                new LanguageProvider(output)
        );
        generator.addProvider(event.includeClient(), new LanguageProviderZhTw(output));
        generator.addProvider(event.includeClient(), new LanguageProviderZhCn(output));
        generator.addProvider(event.includeClient(), new LanguageProviderJaJp(output));
        generator.addProvider(event.includeClient(), new LanguageProviderEsEs(output));
        generator.addProvider(event.includeClient(), new LanguageProviderFrFr(output));
        generator.addProvider(event.includeClient(), new LanguageProviderKoKr(output));
        generator.addProvider(event.includeClient(), new LanguageProviderRuRu(output));
        generator.addProvider(event.includeClient(), new LanguageProviderDeDe(output));
        generator.addProvider(event.includeClient(), new LanguageProviderPtBr(output));
    }
}
