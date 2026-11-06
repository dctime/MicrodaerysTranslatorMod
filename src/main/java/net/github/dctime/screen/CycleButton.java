package net.github.dctime.screen;

import net.minecraft.client.gui.DialogTexts;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 1.16.5 stand-in for vanilla's {@code CycleButton} (added in 1.17). Same builder surface the
 * 2.0.0 screens use -- {@code onOffBuilder(initial)} / {@code builder(valueToText)}, then
 * {@code withValues / withInitialValue / displayOnlyValue / withTooltip / create(x, y, w, h,
 * caption, onValueChange)} -- so those screens' code stays line-for-line comparable with the newer
 * branches and only the import differs.
 * <p>
 * Behaviour follows 1.17+'s CycleButton: a click advances to the next value (Shift+click goes
 * back), the label is "caption: value" unless {@link Builder#displayOnlyValue()} was called, and
 * the per-value tooltip (if any) is exposed through {@link TooltipProvider} for
 * {@link CompatOptionsSubScreen#render} to draw -- 1.16.5 widgets have no tooltip of their own.
 */
public class CycleButton<T> extends Button implements TooltipProvider {

    /** 1.17+'s {@code CycleButton.OnValueChange}. */
    public interface OnValueChange<T> {
        void onValueChange(CycleButton<T> button, T value);
    }

    private final ITextComponent caption;
    private final List<T> values;
    private final Function<T, ITextComponent> valueToText;
    private final boolean displayOnlyValue;
    private final Function<T, List<IReorderingProcessor>> tooltip;
    private final OnValueChange<T> onValueChange;
    private int index;
    private T value;

    private CycleButton(int x, int y, int width, int height, ITextComponent caption, List<T> values, int index,
                        Function<T, ITextComponent> valueToText, boolean displayOnlyValue,
                        Function<T, List<IReorderingProcessor>> tooltip, OnValueChange<T> onValueChange) {
        super(x, y, width, height, caption, b -> {});
        this.caption = caption;
        this.values = values;
        this.index = index;
        this.value = values.get(index);
        this.valueToText = valueToText;
        this.displayOnlyValue = displayOnlyValue;
        this.tooltip = tooltip;
        this.onValueChange = onValueChange;
        updateMessage();
    }

    public static Builder<Boolean> onOffBuilder(boolean initialValue) {
        return new Builder<Boolean>(v -> v ? DialogTexts.OPTION_ON : DialogTexts.OPTION_OFF)
                .withValues(Boolean.TRUE, Boolean.FALSE)
                .withInitialValue(initialValue);
    }

    public static <T> Builder<T> builder(Function<T, ITextComponent> valueToText) {
        return new Builder<>(valueToText);
    }

    @Override
    public void onPress() {
        cycle(Screen.hasShiftDown() ? -1 : 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible) return false;
        if (delta > 0) cycle(-1);
        else if (delta < 0) cycle(1);
        return true;
    }

    private void cycle(int step) {
        index = Math.floorMod(index + step, values.size());
        value = values.get(index);
        updateMessage();
        onValueChange.onValueChange(this, value);
    }

    public T getValue() {
        return value;
    }

    public void setValue(T newValue) {
        int i = values.indexOf(newValue);
        if (i >= 0) {
            index = i;
            value = newValue;
            updateMessage();
        }
    }

    private void updateMessage() {
        ITextComponent valueText = valueToText.apply(value);
        setMessage(displayOnlyValue ? valueText : new TranslationTextComponent("options.generic_value", caption, valueText));
    }

    @Override
    public List<IReorderingProcessor> getTooltipLines() {
        return tooltip == null ? Collections.<IReorderingProcessor>emptyList() : tooltip.apply(value);
    }

    public static final class Builder<T> {
        private final Function<T, ITextComponent> valueToText;
        private List<T> values = Collections.emptyList();
        private T initialValue;
        private boolean displayOnlyValue;
        private Function<T, List<IReorderingProcessor>> tooltip;

        private Builder(Function<T, ITextComponent> valueToText) {
            this.valueToText = valueToText;
        }

        @SafeVarargs
        public final Builder<T> withValues(T... values) {
            return withValues(Arrays.asList(values));
        }

        public Builder<T> withValues(Collection<? extends T> values) {
            if (values.isEmpty()) throw new IllegalArgumentException("No values for cycle button");
            this.values = Collections.unmodifiableList(new ArrayList<T>(values));
            return this;
        }

        public Builder<T> withInitialValue(T initialValue) {
            this.initialValue = initialValue;
            return this;
        }

        public Builder<T> displayOnlyValue() {
            this.displayOnlyValue = true;
            return this;
        }

        public Builder<T> withTooltip(Function<T, List<IReorderingProcessor>> tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public CycleButton<T> create(int x, int y, int width, int height, ITextComponent caption,
                                     OnValueChange<T> onValueChange) {
            if (values.isEmpty()) throw new IllegalStateException("No values for cycle button");
            int index = initialValue == null ? 0 : Math.max(0, values.indexOf(initialValue));
            return new CycleButton<>(x, y, width, height, caption, values, index, valueToText,
                    displayOnlyValue, tooltip, onValueChange);
        }

        public CycleButton<T> create(int x, int y, int width, int height, ITextComponent caption) {
            return create(x, y, width, height, caption, (b, v) -> {});
        }
    }
}
