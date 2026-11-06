package net.github.dctime.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 1.19.2 stand-in for {@code Button.builder(...)} (added in 1.19.3) and the {@code Button.DEFAULT_HEIGHT}
 * / {@code Button.SMALL_WIDTH} constants. Same defaults as the 1.19.3+ builder (150x20 at 0,0), so
 * call sites read {@code CompatButton.builder(msg, onPress).width(...).build()} exactly like the
 * newer branches' {@code Button.builder(...)} chains.
 */
public final class CompatButton {
    public static final int DEFAULT_WIDTH = 150;
    public static final int DEFAULT_HEIGHT = 20;
    public static final int SMALL_WIDTH = 120;

    private CompatButton() {}

    public static Builder builder(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static final class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;

        private Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Button build() {
            return new Button(x, y, width, height, message, onPress);
        }
    }
}
