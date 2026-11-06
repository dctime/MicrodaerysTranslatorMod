package net.github.dctime.screen;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 1.20.1 stand-in for the parts of 1.21's {@code OptionsSubScreen} this package's config screens
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
            button.setX(x);
            button.setY(height - 27);
            addRenderableWidget(button);
            x += button.getWidth() + FOOTER_SPACING;
        }
    }

    /** Fill {@link #list}; called from {@link #init()} and from each screen's own refreshOptions(). */
    protected abstract void addOptions();

    /** 1.21 OptionsSubScreen.addFooter() equivalent. */
    protected List<Button> createFooterButtons() {
        return List.of(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, (HEADER_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }
}
