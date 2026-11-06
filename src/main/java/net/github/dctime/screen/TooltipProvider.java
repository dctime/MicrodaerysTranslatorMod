package net.github.dctime.screen;

import net.minecraft.util.IReorderingProcessor;

import java.util.List;

/**
 * 1.16.5 stand-in for 1.18's {@code TooltipAccessor}: a widget that has hover-tooltip lines for
 * {@link CompatOptionsSubScreen#render} to draw (1.16.5 widgets draw no tooltip of their own).
 * Empty list = no tooltip.
 */
public interface TooltipProvider {
    List<IReorderingProcessor> getTooltipLines();
}
