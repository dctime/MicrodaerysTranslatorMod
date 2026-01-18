package net.github.dctime.compability.jade;

import mcp.mobius.waila.api.Accessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.event.WailaRenderEvent;
import mcp.mobius.waila.api.ui.IElement;
import mcp.mobius.waila.impl.BlockAccessorImpl;
import mcp.mobius.waila.impl.EntityAccessorImpl;
import mcp.mobius.waila.impl.ui.TextElement;
import net.github.dctime.Config;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.github.dctime.libs.Translator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.List;

//@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TestJadeRenderEvent {
//    @SubscribeEvent
//    public static void onJadeRender(WailaRenderEvent.Pre event) {
//        ITooltip tooltip = event.getTooltip();
//        Accessor<?> accessor = event.getAccessor();
////        onTooltipCollected(tooltip, accessor);
//    }

    public static void onTooltipCollected(ITooltip iTooltip, Accessor<?> accessor){
        ItemStack stack = null;
        if ((accessor instanceof BlockAccessorImpl blockAccessor)) {
            stack = blockAccessor.getBlock().asItem().getDefaultInstance();
        }

        if ((accessor instanceof EntityAccessorImpl entityAccessor) && (entityAccessor.getEntity() instanceof ItemEntity itemEntity)) {
            stack = itemEntity.getItem();
        }

        if (!Config.ENABLE_JADE_CONFIG.get()) return;
        for (int jadeIndex = 0; jadeIndex < iTooltip.size(); jadeIndex++) {
            String lineMsg = "";
            List<IElement> elements = iTooltip.get(jadeIndex, IElement.Align.LEFT);
            for (IElement element : elements) {
                if (element instanceof TextElement textElement && textElement.getMessage() != null && textElement.getMessage() instanceof TextComponent textComponent) {
                    lineMsg += textComponent.plainCopy().getString();
                }
            }

            if (!Translator.translationCache.containsKey(lineMsg)) {
                try {
                    if (jadeIndex != 0) {
                        Translator.requestTranslateToTraditionalChinese(lineMsg);
                    } else {
                        Translator.requestTranslateItemStackToTraditionalChinese(lineMsg, stack);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }

            iTooltip.append(jadeIndex, new TextElement(new TextComponent(" " + Translator.translationCache.get(lineMsg)).withStyle(Translator.translatedStyle)));
        }

    }
}
