package net.github.dctime.mixin.jade;

import com.mojang.blaze3d.matrix.MatrixStack;
import mcp.mobius.waila.overlay.OverlayRenderer;
import mcp.mobius.waila.overlay.Tooltip;
import net.github.dctime.compability.jade.TestJadeRenderEvent;
import net.github.dctime.libs.jade.IGetItem;
import net.github.dctime.libs.jade.ITooltipMixin;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade 2.x (1.16.5) hook: appends the translation to each tooltip line's sibling list at the head
 * of every overlay render, recomputes the box size so the longer lines fit, and removes exactly the
 * siblings it added again at the tail -- the Tooltip (and its line components) is reused across
 * frames, so leaving them in would stack one more translation per frame. The translation decision
 * itself lives in {@link TestJadeRenderEvent} so it stays on the same
 * Translator.resolveOrRequestTranslation chain as inventory tooltips (#20).
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public class OverlayRendererMixin {

    private static final List<Integer> beforeTranslationSibingsSizes = new ArrayList<>(100);

    @Inject(method = "renderOverlay(Lmcp/mobius/waila/overlay/Tooltip;Lcom/mojang/blaze3d/matrix/MatrixStack;)V", at = @At("HEAD"), remap = false)
    private static void beforeRenderOverlay(Tooltip tooltip, MatrixStack matrixStack, CallbackInfo ci) {
        beforeTranslationSibingsSizes.clear();
        for (int i = 0; i < tooltip.getLines().size(); i++) {
            int size = tooltip.getLines().get(i).getComponent().getSiblings().size();
            beforeTranslationSibingsSizes.add(i, size);
        }

        ItemStack stack = null;
        if (tooltip instanceof IGetItem) {
            stack = ((IGetItem) tooltip).getItem();
        }
        TestJadeRenderEvent.onTooltipCollected(tooltip, stack);

        if (!(tooltip instanceof ITooltipMixin)) return;
        ((ITooltipMixin) tooltip).computeLinesSelf();
        tooltip.addPadding();
    }

    @Inject(method = "renderOverlay(Lmcp/mobius/waila/overlay/Tooltip;Lcom/mojang/blaze3d/matrix/MatrixStack;)V", at = @At("RETURN"), remap = false)
    private static void afterRenderOverlay(Tooltip tooltip, MatrixStack matrixStack, CallbackInfo ci) {
        for (int i = 0; i < tooltip.getLines().size() && i < beforeTranslationSibingsSizes.size(); i++) {
            List<?> siblings = tooltip.getLines().get(i).getComponent().getSiblings();
            int before = beforeTranslationSibingsSizes.get(i);
            for (int removeIndex = siblings.size() - 1; removeIndex >= before; removeIndex--) {
                siblings.remove(removeIndex);
            }
        }
    }
}
