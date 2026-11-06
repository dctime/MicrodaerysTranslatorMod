package net.github.dctime.mixin.jade;

import com.mojang.blaze3d.vertex.PoseStack;
import mcp.mobius.waila.api.ui.IElement;
import mcp.mobius.waila.overlay.OverlayRenderer;
import mcp.mobius.waila.overlay.TooltipRenderer;
import net.github.dctime.compability.jade.TestJadeRenderEvent;
import net.github.dctime.libs.jade.IGetIcon;
import net.github.dctime.libs.jade.IGetItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        // Jade 5 has no accessor-carrying callback here; the looked-at thing's ItemStack comes from
        // the overlay's icon element (TooltipRendererMixin exposes it, ItemStackElementMixin
        // exposes its stack). Translation logic itself lives in TestJadeRenderEvent so it stays
        // on the same Translator.resolveOrRequestTranslation chain as inventory tooltips (#20).
        ItemStack stack = null;
        if (renderer instanceof IGetIcon iconGetter && iconGetter.getIcon() instanceof IGetItem iGetItem) {
            stack = iGetItem.getItem();
        }
        TestJadeRenderEvent.onTooltipCollected(renderer.getTooltip(), stack);
    }
}
