package net.github.dctime.screen;

import net.minecraft.client.Options;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.TooltipAccessor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 1.20.1/1.19.2 stand-in for the parts of 1.21's {@code OptionsSubScreen} this package's config screens
 * use: a title header, a scrolling {@link #list} that {@link #addOptions()} fills, and a footer
 * (default: one 200px Done = {@link #onClose()}, overridable via {@link #createFooterButtons()}).
 * <p>
 * 1.20.1's own {@code OptionsSubScreen} has none of that (no HeaderAndFooterLayout, no
 * addOptions/addFooter hooks, and its OptionsList only takes OptionInstances), so the 2.0.0 screens
 * extend this instead and otherwise keep their 1.21 code unchanged. Geometry follows 1.20.1's own
 * vanilla options screens: list between y=32 and height-32, footer buttons at height-27.
 * <p>
 * Unlike 1.21's HeaderAndFooterLayout (built once in the constructor), everything here is rebuilt
 * in {@link #init()} -- 1.20.1's resize path re-runs init() -- so the "accumulating OptionsList"
 * problem the screens' refreshOptions() javadoc describes can't happen here either way; the
 * screens keep using clear-and-refill for scroll-position preservation.
 * <p>
 * 1.19.2 additionally lacks {@code StringWidget}, {@code Tooltip}, {@code Button.builder} and
 * {@code MultiLineEditBox}; this package carries small stand-ins with the same call surface
 * ({@link StringWidget}, {@link Tooltip}, {@link CompatButton}, {@link MultiLineEditBox}), and
 * this class draws the hovered widget's tooltip itself (see {@link #render}) because 1.19.2
 * widgets only expose tooltips via {@code TooltipAccessor}.
 */
public abstract class CompatOptionsSubScreen extends Screen {

    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 32;
    private static final int FOOTER_SPACING = 8;

    protected final Screen lastScreen;
    protected final Options options;
    protected WidgetRowList list;

    protected CompatOptionsSubScreen(Screen lastScreen, Options options, Component title) {
        super(title);
        this.lastScreen = lastScreen;
        this.options = options;
    }

    @Override
    protected void init() {
        list = new WidgetRowList(minecraft, width, height, HEADER_HEIGHT, height - FOOTER_HEIGHT);
        addOptions();
        addWidget(list);

        List<Button> footer = createFooterButtons();
        int totalWidth = -FOOTER_SPACING;
        for (Button button : footer) totalWidth += button.getWidth() + FOOTER_SPACING;
        int x = width / 2 - totalWidth / 2;
        for (Button button : footer) {
            button.x = x;
            button.y = height - 27;
            addRenderableWidget(button);
            x += button.getWidth() + FOOTER_SPACING;
        }
    }

    /** Fill {@link #list}; called from {@link #init()} and from each screen's own refreshOptions(). */
    protected abstract void addOptions();

    /** 1.21 OptionsSubScreen.addFooter() equivalent. */
    protected List<Button> createFooterButtons() {
        return List.of(CompatButton.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        list.render(poseStack, mouseX, mouseY, partialTick);
        drawCenteredString(poseStack, font, title, width / 2, (HEADER_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTick);
        // 1.19.2 widgets don't draw their own tooltips: CycleButton only exposes them through
        // TooltipAccessor and vanilla's OptionsSubScreen.tooltipAt() is tied to its own
        // OptionsList. Drawn last so nothing in the list or footer paints over it.
        List<FormattedCharSequence> tooltip = hoveredTooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) renderTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    private List<FormattedCharSequence> hoveredTooltip(int mouseX, int mouseY) {
        // Only rows inside the list's visible band count -- rows scrolled under the header/footer
        // still have (off-screen-clipped) positions that could match the mouse.
        if (mouseY < HEADER_HEIGHT || mouseY >= height - FOOTER_HEIGHT) return List.of();
        for (WidgetRowList.Entry entry : list.children()) {
            for (GuiEventListener child : entry.children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(mouseX, mouseY)
                        && widget instanceof TooltipAccessor accessor) {
                    return accessor.getTooltip();
                }
            }
        }
        return List.of();
    }

    @Override
    public void tick() {
        super.tick();
        if (list != null) list.tickWidgets();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }
}
