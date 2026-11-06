package net.github.dctime.screen;

import net.github.dctime.libs.CollectionUtil;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.list.AbstractOptionList;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.IGuiEventListener;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 1.20.1/1.19.2 stand-in for 1.21's {@code OptionsList.addSmall(AbstractWidget, AbstractWidget)}.
 * <p>
 * 1.20.1's own {@code OptionsList} only accepts {@code OptionInstance}s, but every config screen in
 * this package lays out ARBITRARY widgets (StringWidget labels, EditBoxes, CycleButtons, plain
 * Buttons) two per row -- so this is a minimal scrolling list with the same row model and the same
 * geometry as 1.21's OptionsList: 25px rows, left widget at {@code screenWidth/2 - 155}, right
 * widget 160px further, each widget keeping its own width. Widget positions are (re)assigned every
 * frame in {@link Entry#render}, exactly like vanilla's OptionsList, so mouse hit-testing always
 * matches what's drawn.
 * <p>
 * {@code children().clear()} is supported (AbstractSelectionList's TrackedList implements
 * remove), which is what the screens' {@code refreshOptions()} relies on.
 */
public class WidgetRowList extends AbstractOptionList<WidgetRowList.Entry> {

    static final int ROW_HEIGHT = 25;

    public WidgetRowList(Minecraft minecraft, int width, int height, int top, int bottom) {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
    }

    public void addSmall(Widget left, @Nullable Widget right) {
        addEntry(new Entry(right == null ? CollectionUtil.listOf(left) : CollectionUtil.listOf(left, right), this.width));
    }

    /**
     * 1.19.2: a click only ever reaches the widget under the mouse, and 1.19.2's EditBox only drops
     * its own focus flag when IT is clicked -- so clicking from one EditBox row into another left
     * the first one still drawing a caret (keyboard input correctly follows the new one; this is
     * purely the stale caret). 1.20+'s focus-path handling clears it; here it's done explicitly.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Entry entry : children()) {
            for (Widget widget : entry.widgets) {
                if (widget instanceof TextFieldWidget && !widget.isMouseOver(mouseX, mouseY)) ((TextFieldWidget) widget).setFocus(false);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 1.19.2's EditBox blinks its caret from tick(), which nothing else calls for widgets inside a list. */
    public void tickWidgets() {
        for (Entry entry : children()) {
            for (Widget widget : entry.widgets) {
                if (widget instanceof TextFieldWidget) ((TextFieldWidget) widget).tick();
            }
        }
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.width / 2 + 160;
    }

    public static class Entry extends AbstractOptionList.Entry<Entry> {
        private final List<Widget> widgets;
        private final int listWidth;

        Entry(List<Widget> widgets, int listWidth) {
            this.widgets = widgets;
            this.listWidth = listWidth;
        }

        @Override
        public void render(MatrixStack poseStack, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = listWidth / 2 - 155;
            for (Widget widget : widgets) {
                widget.x = x;
                widget.y = top;
                widget.render(poseStack, mouseX, mouseY, partialTick);
                x += 160;
            }
        }

        @Override
        public List<? extends IGuiEventListener> children() {
            return widgets;
        }

    }
}
