package net.github.dctime.screen;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 1.20.4 port shim for 1.21's {@code OptionsList.addSmall(AbstractWidget, AbstractWidget)}: 1.20.4's
 * own {@code OptionsList} only accepts {@code OptionInstance}s, but every config screen here puts
 * plain widgets (StringWidget labels, EditBoxes, CycleButtons with custom callbacks) into rows.
 * Same geometry as 1.21's: 25px rows, 310px row width, left widget at {@code screen.width/2 - 155},
 * right widget 160px further. See {@link OptionsSubScreenCompat}.
 */
public class OptionsListCompat extends ContainerObjectSelectionList<OptionsListCompat.Entry> {

    private static final int ITEM_HEIGHT = 25;

    private final Screen screen;

    public OptionsListCompat(Minecraft minecraft, int width, OptionsSubScreenCompat screen) {
        super(minecraft, width, contentHeight(screen), screen.layout.getHeaderHeight(), ITEM_HEIGHT);
        this.centerListVertically = false;
        this.screen = screen;
    }

    private static int contentHeight(OptionsSubScreenCompat screen) {
        return Math.max(0, screen.height - screen.layout.getHeaderHeight() - screen.layout.getFooterHeight());
    }

    public void addSmall(AbstractWidget left, @Nullable AbstractWidget right) {
        addEntry(new Entry(right == null ? ImmutableList.of(left) : ImmutableList.of(left, right), screen));
    }

    void updateSize(int width, HeaderAndFooterLayout layout) {
        setRectangle(width, Math.max(0, screen.height - layout.getHeaderHeight() - layout.getFooterHeight()), 0, layout.getHeaderHeight());
        // keep the scroll offset inside the new bounds (same clamp setScrollAmount itself applies)
        setScrollAmount(getScrollAmount());
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    @Override
    protected int getScrollbarPosition() {
        return width / 2 + 160;
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final List<AbstractWidget> children;
        private final Screen screen;

        Entry(List<AbstractWidget> children, Screen screen) {
            this.children = children;
            this.screen = screen;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int offset = 0;
            int x = screen.width / 2 - 155;
            for (AbstractWidget widget : children) {
                widget.setPosition(x + offset, top);
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
                offset += 160;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return children;
        }
    }
}
