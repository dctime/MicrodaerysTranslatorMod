package net.github.dctime.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.IReorderingProcessor;

import java.util.List;

/**
 * 1.19.2 stand-in for vanilla's {@code Tooltip} class (added in 1.19.3). 1.19.2's
 * {@code CycleButton.Builder.withTooltip} takes an {@code OptionInstance.TooltipSupplier} -- a
 * {@code Function<T, List<FormattedCharSequence>>} -- so {@link #create} returns the already
 * wrapped lines. That lets the 2.0.0 screens keep their {@code .withTooltip(v -> Tooltip.create(...))}
 * call sites unchanged; {@link CompatOptionsSubScreen#render} is what actually draws them (1.19.2
 * widgets only expose tooltips through {@code TooltipAccessor}, the screen has to render them).
 * Wrap width 200 matches 1.19.2's own option tooltips.
 */
public final class Tooltip {
    private Tooltip() {}

    public static List<IReorderingProcessor> create(ITextComponent message) {
        return Minecraft.getInstance().font.split(message, 200);
    }
}
