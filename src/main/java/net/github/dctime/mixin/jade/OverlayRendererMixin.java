package net.github.dctime.mixin.jade;

import com.mojang.blaze3d.matrix.MatrixStack;
import mcp.mobius.waila.overlay.OverlayRenderer;
import mcp.mobius.waila.overlay.RayTracing;
import mcp.mobius.waila.overlay.Tooltip;
import mezz.jei.gui.TooltipRenderer;
import net.github.dctime.Config;
import net.github.dctime.libs.TooltipRelated;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.jade.IGetIcon;
import net.github.dctime.libs.jade.IGetItem;
import net.github.dctime.libs.jade.ITooltipMixin;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = OverlayRenderer.class, remap = false)
public class OverlayRendererMixin {

    private static List<Integer> beforeTranslationSibingsSizes = new ArrayList<>(100);

    @Inject(method = "renderOverlay(Lmcp/mobius/waila/overlay/Tooltip;Lcom/mojang/blaze3d/matrix/MatrixStack;)V", at = @At("HEAD"), remap = false)
    private static void beforeRenderOverlay(Tooltip tooltip, MatrixStack matrixStack, CallbackInfo ci) {
        // Get lines and dimension first and store it and delete it. call computeLines and addPadding

        beforeTranslationSibingsSizes.clear();
        for (int i = 0; i < tooltip.getLines().size(); i++) {
            int size = tooltip.getLines().get(i).getComponent().getSiblings().size();
            beforeTranslationSibingsSizes.add(i, size);
        }
        onTooltipCollected(tooltip);

        if (!(tooltip instanceof ITooltipMixin)) return;
        ITooltipMixin iTooltipMixin = (ITooltipMixin) tooltip;
        iTooltipMixin.computeLinesSelf();
        tooltip.addPadding();
//        System.out.println("Width" + tooltip.getPosition().width + " Height" + tooltip.getPosition().height);
//        iTooltipMixin.removeLineAndSize();
//        tooltip.computeLines();
    }

    @Inject(method = "renderOverlay(Lmcp/mobius/waila/overlay/Tooltip;Lcom/mojang/blaze3d/matrix/MatrixStack;)V", at = @At("RETURN"), remap = false)
    private static void afterRenderOverlay(Tooltip tooltip, MatrixStack matrixStack, CallbackInfo ci) {
        for (int i = 0; i < tooltip.getLines().size(); i++) {
            int size = tooltip.getLines().get(i).getComponent().getSiblings().size();
            if (beforeTranslationSibingsSizes.get(i) < size) {
                for (int removeIndex = size-1; removeIndex >= beforeTranslationSibingsSizes.get(i); removeIndex--) {
                    tooltip.getLines().get(i).getComponent().getSiblings().remove(removeIndex);
                }
            }
        }
    }

    private static void onTooltipCollected(Tooltip tooltip){
        ItemStack stack = null;

        if (tooltip instanceof IGetItem) {
            IGetItem iGetItem = (IGetItem) tooltip;
            stack = iGetItem.getItem();
        }

        for (int i = 0; i < tooltip.getLines().size(); i++) {
            if (!(tooltip.getLines().get(i).getComponent() instanceof TextComponent)) continue;
            TextComponent textComponent = (TextComponent) tooltip.getLines().get(i).getComponent();
            String original = TooltipRelated.getStringFromComponent(textComponent);
//            System.out.println(original);

            if (!Translator.translationCache.containsKey(original)) {
                try {
                    if (i != 0) {
                        Translator.requestTranslateToTraditionalChinese(original);
                    } else {
                        Translator.requestTranslateItemStackToTraditionalChinese(original, stack);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            try {
                textComponent.append(new StringTextComponent(" " + Translator.translationCache.get(original)).withStyle(Translator.translatedStyle));
            } catch (UnsupportedOperationException e) {
                // RenderableTextComponent
            }

        }
    }
}
