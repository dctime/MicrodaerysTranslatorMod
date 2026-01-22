package net.github.dctime.mixin.jade;

import mcp.mobius.waila.overlay.Tooltip;
import net.github.dctime.libs.jade.ISetSizeToNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.awt.*;

@Mixin(value = Tooltip.Line.class, remap = false)
public class TooltipLineMixin implements ISetSizeToNull {
    @Shadow
    private Dimension size;

    public void setSizeToNull() {
        size = null;
    }
}
