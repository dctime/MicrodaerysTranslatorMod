package net.github.dctime.compability.jade;

import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElement;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.EntityAccessorImpl;
import snownee.jade.impl.ui.TextElement;

import java.io.IOException;
import java.util.List;

public class TestTooltipCollectedCallback implements JadeTooltipCollectedCallback {

    @Override
    public void onTooltipCollected(ITooltip iTooltip, Accessor<?> accessor){
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
                if (element instanceof TextElement textElement) {
                    lineMsg += textElement.getMessage();
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

            iTooltip.append(jadeIndex, new TextElement(Component.literal(" " + Translator.translationCache.get(lineMsg)).withStyle(Translator.translatedStyle)));
        }

    }
}
