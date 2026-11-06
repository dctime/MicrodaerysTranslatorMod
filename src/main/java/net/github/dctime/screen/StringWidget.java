package net.github.dctime.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.LanguageMap;

/**
 * 1.19.2 stand-in for vanilla's {@code StringWidget} (added in 1.19.4): a non-interactive text
 * label that can sit in a {@link WidgetRowList} row like any other widget. Same constructors and
 * the same {@link #alignLeft()} / {@link #setColor(int)} surface the 2.0.0 screens use, so those
 * screens' code stays line-for-line identical to the newer branches (only the import differs) --
 * which keeps future version-to-version merges into this branch conflict-free in the screens.
 * <p>
 * Behaviour matches 1.20.1's StringWidget: the auto-sized constructor is exactly the text's width
 * and one line tall; text too wide for an explicit width is clipped with "..."; text is vertically
 * centred in the widget's height; centred horizontally unless {@link #alignLeft()} was called.
 */
public class StringWidget extends Widget {

    private final FontRenderer font;
    private int color = 0xFFFFFF;
    private boolean alignLeft = false;

    public StringWidget(ITextComponent message, FontRenderer font) {
        this(font.width(message.getVisualOrderText()), 9, message, font);
    }

    public StringWidget(int width, int height, ITextComponent message, FontRenderer font) {
        super(0, 0, width, height, message);
        this.font = font;
        // Labels must never take clicks/focus away from the real widgets next to them.
        this.active = false;
    }

    public StringWidget alignLeft() {
        this.alignLeft = true;
        return this;
    }

    public StringWidget alignCenter() {
        this.alignLeft = false;
        return this;
    }

    public StringWidget setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    public void renderButton(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        ITextComponent message = getMessage();
        IReorderingProcessor text = font.width(message) > width ? clipText(message, width) : message.getVisualOrderText();
        int textWidth = font.width(text);
        int drawX = alignLeft ? x : x + (width - textWidth) / 2;
        int drawY = y + (height - 9 + 1) / 2;
        font.drawShadow(poseStack, text, drawX, drawY, color);
    }

    private IReorderingProcessor clipText(ITextComponent message, int maxWidth) {
        ITextProperties ellipsis = ITextProperties.of("...");
        ITextProperties clipped = font.substrByWidth(message, maxWidth - font.width(ellipsis));
        return LanguageMap.getInstance().getVisualOrder(ITextProperties.composite(clipped, ellipsis));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean changeFocus(boolean forward) {
        return false;
    }

}
