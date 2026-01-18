package net.github.dctime.mixin.jade;

import com.mojang.blaze3d.vertex.PoseStack;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.ui.IElement;
import mcp.mobius.waila.impl.ui.ItemStackElement;
import mcp.mobius.waila.impl.ui.TextElement;
import mcp.mobius.waila.overlay.OverlayRenderer;
import mcp.mobius.waila.overlay.TooltipRenderer;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.jade.IGetIcon;
import net.github.dctime.libs.jade.IGetItem;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = OverlayRenderer.class, remap = false)
public class OverlayRendererMixin {

    private static List<Integer> beforeTranslationSizes = new ArrayList<>(100);

    @Inject(method = "renderOverlay(Lmcp/mobius/waila/overlay/TooltipRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), remap = false)
    private static void beforeRenderOverlay(TooltipRenderer tooltip, PoseStack matrixStack, CallbackInfo ci) {
        beforeTranslationSizes.clear();
        for (int i = 0; i < tooltip.getTooltip().lines.size(); i++) {
            int size = tooltip.getTooltip().lines.get(i).getAlignedElements(IElement.Align.LEFT).size();
            beforeTranslationSizes.add(i, size);
        }
        onTooltipCollected(tooltip);
        tooltip.computeSize();
    }

    @Inject(method = "renderOverlay(Lmcp/mobius/waila/overlay/TooltipRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"), remap = false)
    private static void afterRenderOverlay(TooltipRenderer tooltip, PoseStack matrixStack, CallbackInfo ci) {
        for (int i = 0; i < tooltip.getTooltip().lines.size(); i++) {
            int size = tooltip.getTooltip().lines.get(i).getAlignedElements(IElement.Align.LEFT).size();
            if (beforeTranslationSizes.get(i) < size) {
                for (int removeIndex = size-1; removeIndex >= beforeTranslationSizes.get(i); removeIndex--) {
                    tooltip.getTooltip().lines.get(i).getAlignedElements(IElement.Align.LEFT).remove(removeIndex);
                }
            }
        }
    }

    private static void onTooltipCollected(TooltipRenderer renderer){
        ItemStack stack = null;
        if (renderer instanceof IGetIcon iconGetter && iconGetter.getIcon() instanceof IGetItem iGetItem) {
            stack = iGetItem.getItem();
        }
//        if ((accessor instanceof BlockAccessorImpl blockAccessor)) {
//            stack = blockAccessor.getBlock().asItem().getDefaultInstance();
//        }
//
//        if ((accessor instanceof EntityAccessorImpl entityAccessor) && (entityAccessor.getEntity() instanceof ItemEntity itemEntity)) {
//            stack = itemEntity.getItem();
//        }

        if (!Config.ENABLE_JADE_CONFIG.get()) return;
        for (int jadeIndex = 0; jadeIndex < renderer.getTooltip().size(); jadeIndex++) {
            String lineMsg = "";
            List<IElement> elements = renderer.getTooltip().get(jadeIndex, IElement.Align.LEFT);
            for (IElement element : elements) {
                if (element instanceof TextElement textElement && textElement.getMessage() != null && textElement.getMessage() instanceof TextComponent textComponent) {
                    lineMsg += textComponent.plainCopy().getString();
                }

                if (element instanceof TextElement textElement && textElement.getMessage() != null && textElement.getMessage() instanceof TranslatableComponent translatableComponent) {
                    lineMsg += translatableComponent.getString();
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

            renderer.getTooltip().append(jadeIndex, new TextElement(new TextComponent(" " + Translator.translationCache.get(lineMsg)).withStyle(Translator.translatedStyle)));
        }

    }
}
