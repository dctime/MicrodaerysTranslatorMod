package net.github.dctime.screen;

import net.minecraft.network.chat.TranslatableComponent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.19.2 stand-in for vanilla's {@code MultiLineEditBox} (added in 1.19.3), with the same
 * constructor and the {@code setCharacterLimit/setValue/getValue/setValueListener} surface
 * {@link CustomPromptEditScreen} uses -- so that screen's code is unchanged from the newer branches.
 * <p>
 * Deliberately small: word-wrapped multi-line text with a caret, mouse-wheel scrolling, click/drag
 * to place the caret and select, Shift+arrows/Home/End selection, Ctrl+A/C/X/V, Backspace/Delete
 * (Ctrl = whole word), Enter for a newline. Escape is NOT consumed, so it still closes the screen
 * (= cancel, see {@link CustomPromptEditScreen#onClose()}).
 * <p>
 * Text offsets are plain {@code String} indices (UTF-16 units), the same unit vanilla's EditBox
 * uses; the prompt templates this edits are BMP text, so no surrogate-pair handling is attempted.
 */
public class MultiLineEditBox extends AbstractWidget {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 9;
    private static final int SCROLLBAR_WIDTH = 4;

    private final Font font;
    private final Component placeholder;
    private String value = "";
    private int characterLimit = Integer.MAX_VALUE;
    private int cursor = 0;
    private int selectionAnchor = 0;
    private int scrollLine = 0;
    private Consumer<String> valueListener = v -> {};
    private List<int[]> lines = new ArrayList<>(); // {start, end} offsets into value, end exclusive, newline excluded

    public MultiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        super(x, y, width, height, message);
        this.font = font;
        this.placeholder = placeholder;
        relayout();
    }

    public void setCharacterLimit(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Character limit cannot be negative");
        this.characterLimit = limit;
    }

    public void setValueListener(Consumer<String> listener) {
        this.valueListener = listener;
    }

    public void setValue(String newValue) {
        this.value = truncate(newValue == null ? "" : newValue);
        this.cursor = this.value.length();
        this.selectionAnchor = this.cursor;
        relayout();
        scrollToCursor();
        valueListener.accept(this.value);
    }

    public String getValue() {
        return value;
    }

    // ---- layout -------------------------------------------------------------------------------

    private int textWidth() {
        return width - PADDING * 2 - SCROLLBAR_WIDTH;
    }

    private int visibleLines() {
        return Math.max(1, (height - PADDING * 2) / LINE_HEIGHT);
    }

    private void relayout() {
        List<int[]> result = new ArrayList<>();
        int maxWidth = Math.max(1, textWidth());
        int paragraphStart = 0;
        while (true) {
            int newline = value.indexOf('\n', paragraphStart);
            int paragraphEnd = newline < 0 ? value.length() : newline;
            int lineStart = paragraphStart;
            if (lineStart == paragraphEnd) {
                result.add(new int[]{lineStart, lineStart});
            }
            while (lineStart < paragraphEnd) {
                String rest = value.substring(lineStart, paragraphEnd);
                int fit = font.getSplitter().plainIndexAtWidth(rest, maxWidth, Style.EMPTY);
                if (fit >= rest.length()) {
                    result.add(new int[]{lineStart, paragraphEnd});
                    break;
                }
                fit = Math.max(1, fit);
                int lastSpace = rest.lastIndexOf(' ', fit);
                int breakAt = lastSpace > 0 ? lastSpace + 1 : fit; // keep the space on the upper line
                result.add(new int[]{lineStart, lineStart + breakAt});
                lineStart += breakAt;
            }
            if (newline < 0) break;
            paragraphStart = newline + 1;
        }
        lines = result;
        scrollLine = Mth.clamp(scrollLine, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
    }

    private int lineOf(int offset) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (offset >= lines.get(i)[0]) return i;
        }
        return 0;
    }

    private void scrollToCursor() {
        int line = lineOf(cursor);
        if (line < scrollLine) scrollLine = line;
        else if (line >= scrollLine + visibleLines()) scrollLine = line - visibleLines() + 1;
        scrollLine = Mth.clamp(scrollLine, 0, maxScroll());
    }

    private int offsetAt(double mouseX, double mouseY) {
        int line = Mth.clamp((int) ((mouseY - y - PADDING) / LINE_HEIGHT) + scrollLine, 0, lines.size() - 1);
        int[] range = lines.get(line);
        String text = value.substring(range[0], range[1]);
        int index = font.getSplitter().plainIndexAtWidth(text, (int) Math.max(0, mouseX - x - PADDING), Style.EMPTY);
        return range[0] + Math.min(index, text.length());
    }

    // ---- editing ------------------------------------------------------------------------------

    private String truncate(String s) {
        return s.length() > characterLimit ? s.substring(0, characterLimit) : s;
    }

    private boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    private int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    private int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    private void insert(String text) {
        String filtered = text.replace("\r\n", "\n").replace('\r', '\n');
        int start = selectionStart();
        int end = selectionEnd();
        int room = characterLimit - (value.length() - (end - start));
        if (room <= 0 && filtered.length() > 0) return;
        if (filtered.length() > room) filtered = filtered.substring(0, room);
        value = value.substring(0, start) + filtered + value.substring(end);
        cursor = start + filtered.length();
        selectionAnchor = cursor;
        changed();
    }

    private void deleteRange(int start, int end) {
        if (start >= end) return;
        value = value.substring(0, start) + value.substring(end);
        cursor = start;
        selectionAnchor = cursor;
        changed();
    }

    private void changed() {
        relayout();
        scrollToCursor();
        valueListener.accept(value);
    }

    private void moveCursor(int newCursor, boolean extendSelection) {
        cursor = Mth.clamp(newCursor, 0, value.length());
        if (!extendSelection) selectionAnchor = cursor;
        scrollToCursor();
    }

    private int wordBoundary(int from, int direction) {
        int i = from;
        if (direction < 0) {
            while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
            while (i > 0 && !Character.isWhitespace(value.charAt(i - 1))) i--;
        } else {
            while (i < value.length() && !Character.isWhitespace(value.charAt(i))) i++;
            while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        }
        return i;
    }

    private int verticalMove(int lineDelta) {
        int line = lineOf(cursor);
        int target = line + lineDelta;
        if (target < 0) return 0;
        if (target >= lines.size()) return value.length();
        int column = font.width(value.substring(lines.get(line)[0], cursor));
        int[] range = lines.get(target);
        String text = value.substring(range[0], range[1]);
        return range[0] + Math.min(font.getSplitter().plainIndexAtWidth(text, column, Style.EMPTY), text.length());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        boolean shift = Screen.hasShiftDown();
        boolean ctrl = Screen.hasControlDown();
        if (Screen.isSelectAll(keyCode)) {
            selectionAnchor = 0;
            cursor = value.length();
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            if (hasSelection()) Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(selectionStart(), selectionEnd()));
            return true;
        }
        if (Screen.isCut(keyCode)) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(selectionStart(), selectionEnd()));
                deleteRange(selectionStart(), selectionEnd());
            }
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insert("\n");
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteRange(selectionStart(), selectionEnd());
                else if (cursor > 0) deleteRange(ctrl ? wordBoundary(cursor, -1) : cursor - 1, cursor);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteRange(selectionStart(), selectionEnd());
                else if (cursor < value.length()) deleteRange(cursor, ctrl ? wordBoundary(cursor, 1) : cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (!shift && hasSelection()) moveCursor(selectionStart(), false);
                else moveCursor(ctrl ? wordBoundary(cursor, -1) : cursor - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!shift && hasSelection()) moveCursor(selectionEnd(), false);
                else moveCursor(ctrl ? wordBoundary(cursor, 1) : cursor + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveCursor(verticalMove(-1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveCursor(verticalMove(1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                moveCursor(verticalMove(-visibleLines()), shift);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                moveCursor(verticalMove(visibleLines()), shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursor(ctrl ? 0 : lines.get(lineOf(cursor))[0], shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursor(ctrl ? value.length() : lines.get(lineOf(cursor))[1], shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!isFocused() || !SharedConstants.isAllowedChatCharacter(c)) return false;
        insert(Character.toString(c));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = visible && isMouseOver(mouseX, mouseY);
        setFocused(inside);
        if (!inside || button != 0) return inside;
        moveCursor(offsetAt(mouseX, mouseY), Screen.hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isFocused() || button != 0) return false;
        moveCursor(offsetAt(mouseX, mouseY), true);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;
        scrollLine = Mth.clamp(scrollLine - (int) Math.signum(delta) * 3, 0, maxScroll());
        return true;
    }

    @Override
    public boolean changeFocus(boolean forward) {
        if (!visible || !active) return false;
        setFocused(!isFocused());
        return isFocused();
    }

    // ---- rendering ----------------------------------------------------------------------------

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        fill(poseStack, x - 1, y - 1, x + width + 1, y + height + 1, isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        fill(poseStack, x, y, x + width, y + height, 0xFF000000);

        int textX = x + PADDING;
        int textY = y + PADDING;
        if (value.isEmpty() && !isFocused()) {
            font.drawShadow(poseStack, placeholder, textX, textY, 0xFF808080);
            return;
        }

        int selStart = selectionStart();
        int selEnd = selectionEnd();
        int last = Math.min(lines.size(), scrollLine + visibleLines());
        for (int i = scrollLine; i < last; i++) {
            int[] range = lines.get(i);
            int lineY = textY + (i - scrollLine) * LINE_HEIGHT;
            if (hasSelection() && selStart <= range[1] && selEnd >= range[0]) {
                int from = Math.max(selStart, range[0]);
                int to = Math.min(selEnd, range[1]);
                int x0 = textX + font.width(value.substring(range[0], from));
                int x1 = textX + font.width(value.substring(range[0], to));
                if (x1 == x0 && selEnd > range[1]) x1 = x0 + 2; // make a selected empty line/newline visible
                fill(poseStack, x0, lineY - 1, x1, lineY + LINE_HEIGHT, 0xFF3355AA);
            }
            font.drawShadow(poseStack, value.substring(range[0], range[1]), textX, lineY, 0xFFE0E0E0);
        }

        if (isFocused() && (Util.getMillis() / 300L) % 2L == 0L) {
            int line = lineOf(cursor);
            if (line >= scrollLine && line < last) {
                int[] range = lines.get(line);
                int caretX = textX + font.width(value.substring(range[0], Math.min(cursor, range[1])));
                int caretY = textY + (line - scrollLine) * LINE_HEIGHT;
                fill(poseStack, caretX, caretY - 1, caretX + 1, caretY + LINE_HEIGHT, 0xFFD0D0D0);
            }
        }

        if (maxScroll() > 0) {
            int trackTop = y + 1;
            int trackHeight = height - 2;
            int barHeight = Math.max(8, trackHeight * visibleLines() / lines.size());
            int barTop = trackTop + (trackHeight - barHeight) * scrollLine / maxScroll();
            int barX = x + width - SCROLLBAR_WIDTH - 1;
            fill(poseStack, barX, barTop, barX + SCROLLBAR_WIDTH, barTop + barHeight, 0xFF808080);
        }
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, new TranslatableComponent("narration.edit_box", getValue()));
    }
}
