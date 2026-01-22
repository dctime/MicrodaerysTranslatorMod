package net.github.dctime.mixin.jade;

import mcp.mobius.waila.overlay.Tooltip;
import mezz.jei.gui.TooltipRenderer;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.jade.IGetIcon;
import net.github.dctime.libs.jade.IGetItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = TooltipRenderer.class, remap = false)
public abstract class TooltipRendererMixin implements IGetIcon {
//    @Shadow @Final
//    private Tooltip tooltip;

//    @Shadow
//    IElement icon;
//
//    @Override
//    public IElement getIcon() {
//        return icon;
//    }

//    @Shadow
//    public abstract void computeSize();

//    List<Integer> beforeTranslationSizes;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        // do initialisation stuff
//        beforeTranslationSizes = new ArrayList<>(100);
    }

//    @Inject(method = "draw", at = @At("HEAD"), remap = false)
//    public void beforeDraw(PoseStack matrixStack, CallbackInfo ci) {
////        beforeTranslationSizes.clear();
////        for (int i = 0; i < tooltip.lines.size(); i++) {
////            int size = tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT).size();
////            beforeTranslationSizes.add(i, size);
////        }
////        onTooltipCollected(tooltip, icon);
//    }
//
//    @Inject(method = "draw", at = @At("RETURN"), remap = false)
//    public void afterDraw(PoseStack matrixStack, CallbackInfo ci) {
////        for (int i = 0; i < tooltip.lines.size(); i++) {
////            int size = tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT).size();
////            if (beforeTranslationSizes.get(i) < size) {
////                for (int removeIndex = size-1; removeIndex >= beforeTranslationSizes.get(i); removeIndex--) {
////                    tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT).remove(removeIndex);
////                }
////            }
////        }
//    }



}
