package net.github.dctime.mixin.jade;

import com.mojang.blaze3d.vertex.PoseStack;
import mcp.mobius.waila.api.Accessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.ui.IElement;
import mcp.mobius.waila.impl.BlockAccessorImpl;
import mcp.mobius.waila.impl.EntityAccessorImpl;
import mcp.mobius.waila.impl.Tooltip;
import mcp.mobius.waila.impl.ui.ItemStackElement;
import mcp.mobius.waila.impl.ui.TextElement;
import mcp.mobius.waila.overlay.TooltipRenderer;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.jade.IGetIcon;
import net.github.dctime.libs.jade.IGetItem;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
    @Shadow @Final
    private Tooltip tooltip;

    @Shadow
    IElement icon;

    @Override
    public IElement getIcon() {
        return icon;
    }

    @Shadow
    public abstract void computeSize();

//    List<Integer> beforeTranslationSizes;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        // do initialisation stuff
//        beforeTranslationSizes = new ArrayList<>(100);
    }

    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    public void beforeDraw(PoseStack matrixStack, CallbackInfo ci) {
//        beforeTranslationSizes.clear();
//        for (int i = 0; i < tooltip.lines.size(); i++) {
//            int size = tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT).size();
//            beforeTranslationSizes.add(i, size);
//        }
//        onTooltipCollected(tooltip, icon);
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    public void afterDraw(PoseStack matrixStack, CallbackInfo ci) {
//        for (int i = 0; i < tooltip.lines.size(); i++) {
//            int size = tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT).size();
//            if (beforeTranslationSizes.get(i) < size) {
//                for (int removeIndex = size-1; removeIndex >= beforeTranslationSizes.get(i); removeIndex--) {
//                    tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT).remove(removeIndex);
//                }
//            }
//        }
    }



}
