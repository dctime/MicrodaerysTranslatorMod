package net.github.dctime.mixin.jade;

import mcp.mobius.waila.impl.Tooltip;
import net.github.dctime.libs.jade.ISetSizeToNull;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Tooltip.Line.class, remap = false)
public class TooltipLineMixin implements ISetSizeToNull {
    @Shadow
    private Vec2 size;

    public void setSizeToNull() {
        size = null;
    }
}
