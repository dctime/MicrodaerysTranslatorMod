package net.github.dctime.events;

import net.github.dctime.Config;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.github.dctime.libs.TooltipRelated;
import net.github.dctime.libs.Translator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static net.github.dctime.libs.Translator.translationCache;

@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT)
public class RenderTooltipEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderTooltipEvent.class);


    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Config.ENABLE_TOOLTIP_TRANSLATION.get()) return;
        ItemStack stack = event.getItemStack();
        List<? extends ITextProperties> elements = event.getToolTip();

        TooltipRelated.translateListOfText(elements, stack);
    }
}
