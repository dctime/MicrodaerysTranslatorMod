package net.github.dctime.libs.jade;

import mcp.mobius.waila.api.RenderableTextComponent;
import mcp.mobius.waila.api.impl.TaggedTextComponent;
import net.minecraft.util.text.TextComponent;

public class JadeTooltipRelated {
    public static boolean jadeRelatedComponentCheck(TextComponent component) {
        return !(component instanceof RenderableTextComponent) && !(component instanceof TaggedTextComponent);
    }
}
