package net.github.dctime.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;

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
public class StringWidget extends AbstractWidget {

    private final Font font;
    private int color = 0xFFFFFF;
    private boolean alignLeft = false;

    public StringWidget(Component message, Font font) {
        this(font.width(message.getVisualOrderText()), 9, message, font);
    }

    public StringWidget(int width, int height, Component message, Font font) {
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
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Component message = getMessage();
        FormattedCharSequence text = font.width(message) > width ? clipText(message, width) : message.getVisualOrderText();
        int textWidth = font.width(text);
        int drawX = alignLeft ? x : x + (width - textWidth) / 2;
        int drawY = y + (height - 9 + 1) / 2;
        font.drawShadow(poseStack, text, drawX, drawY, color);
    }

    private FormattedCharSequence clipText(Component message, int maxWidth) {
        FormattedText ellipsis = FormattedText.of("...");
        FormattedText clipped = font.substrByWidth(message, maxWidth - font.width(ellipsis));
        return Language.getInstance().getVisualOrder(FormattedText.composite(clipped, ellipsis));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean changeFocus(boolean forward) {
        return false;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
