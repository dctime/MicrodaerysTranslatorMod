package net.github.dctime.mixin.jade;

import com.llamalad7.mixinextras.sugar.Local;
import mcp.mobius.waila.api.ui.IElement;
import mcp.mobius.waila.impl.Tooltip;
import net.github.dctime.libs.jade.ISetSizeToNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Tooltip.class, remap = false)
public class TooltipMixin {

    @Inject(method = "append", at = @At(value = "INVOKE", target = "Lmcp/mobius/waila/impl/Tooltip$Line;getAlignedElements(Lmcp/mobius/waila/api/ui/IElement$Align;)Ljava/util/List;"), remap = false)
    public void appendModifyLastLine(int index, IElement element, CallbackInfo ci, @Local Tooltip.Line lastLine) {
        if (lastLine instanceof ISetSizeToNull line) {
            line.setSizeToNull();
        }
    }
}
