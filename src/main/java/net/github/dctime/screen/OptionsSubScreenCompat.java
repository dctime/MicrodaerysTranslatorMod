package net.github.dctime.screen;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 1.20.4 port shim: a stand-in for 1.21's {@code net.minecraft.client.gui.screens.options.OptionsSubScreen}
 * (layout + {@code list} + {@code addOptions()}/{@code addFooter()} hooks), which 1.20.4's own
 * {@code OptionsSubScreen} (just {@code lastScreen}/{@code options}) does not have. Every config
 * screen in this package was written against the 1.21 shape; keeping the SAME hook names here
 * lets those screens stay line-for-line identical to the 1.21.1 branch instead of each one
 * re-implementing its own positioning -- a diff between branches then only shows real behavior
 * changes, not layout plumbing.
 * <p>
 * Mirrors 1.21's lifecycle exactly: {@link #init()} builds title/contents/footer ONCE, and
 * {@link #repositionElements()} (window resize, or coming back from a sub-screen -- 1.20.4's
 * {@code Screen.init(Minecraft,int,int)} only calls {@code init()} the first time, same as 1.21)
 * only re-arranges. Overriding repositionElements matters: 1.20.4's default implementation is
 * {@code rebuildWidgets()}, which would re-run {@code init()} and add a second title/footer into
 * the same {@link #layout} -- the same accumulating-ghost-widgets bug {@code
 * TranslatorConfigScreen#refreshOptions()}'s javadoc describes for 1.21.
 */
public abstract class OptionsSubScreenCompat extends OptionsSubScreen {

    public final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33);
    protected OptionsListCompat list;

    protected OptionsSubScreenCompat(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Override
    protected void init() {
        addTitle();
        addContents();
        addFooter();
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    protected void addTitle() {
        layout.addToHeader(new StringWidget(title, font));
    }

    protected void addContents() {
        list = addRenderableWidget(new OptionsListCompat(minecraft, width, this));
        addOptions();
    }

    protected abstract void addOptions();

    protected void addFooter() {
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) list.updateSize(width, layout);
    }

    /** Same look as vanilla 1.20.4 option screens (e.g. {@code SimpleOptionsSubScreen}). */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderDirtBackground(guiGraphics);
    }
}
