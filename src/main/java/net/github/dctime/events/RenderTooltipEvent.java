package net.github.dctime.events;

import com.mojang.datafixers.util.Either;
import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.Translator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class RenderTooltipEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderTooltipEvent.class);
    @SubscribeEvent
    public static void onRenderTooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
        if (!Config.ENABLE_TOOLTIP_TRANSLATION.get()) return;
        ItemStack stack = event.getItemStack();
        var elements = event.getTooltipElements();

        for (int i = 0; i < elements.size(); i++) {
            var e = elements.get(i);
            int finalI = i;
            e.ifLeft(text -> {
                String original = text.getString();
                if (Translator.isModNameLine(stack, original)) return; // "Minecraft"/"FTB Quests": leave untouched, don't waste an AI call on a brand name
                String translated;
                if (Translator.textInCache(original))
                    translated = Translator.getTranslationFromCache(original);
                else if (finalI == 0 && Translator.tryOfficialTranslationForItemName(stack, original))
                    translated = Translator.getTranslationFromCache(original);
                else if (finalI != 0 && Translator.tryOfficialTranslationForEnchantmentLine(stack, original))
                    translated = Translator.getTranslationFromCache(original);
                else if (finalI != 0 && Translator.tryOfficialTranslationForAttributeModifierHeaderLine(original))
                    translated = Translator.getTranslationFromCache(original);
                else {
                    try {
                        if (finalI != 0) {
                            Translator.requestTranslateToTraditionalChinese(original);
                        } else {
                            Translator.requestTranslateItemStackToTraditionalChinese(original, stack);
                        }
                    } catch (IOException ex) {
                        LOGGER.warn("IO Exception while translating: " + ex.getMessage());
                    } catch (InterruptedException ex) {
                        LOGGER.warn("Interrupted Exception while translating: " + ex.getMessage());
                    }
                    return;
//                    if (translated == null) return;
//                    translationCache.put(original, translated);
                }
                Component replaced;

                if (text instanceof Component textComponent)
                    replaced = textComponent.copy().append(Component.literal(" " + translated).setStyle(Translator.translatedStyle));
                else
                // Example transformation: prepend and uppercase
                    replaced = Component.literal(text.getString()).append(Component.literal(" " + translated).setStyle(Translator.translatedStyle));
                elements.set(finalI, Either.left(replaced));
            });
        }
    }
}
