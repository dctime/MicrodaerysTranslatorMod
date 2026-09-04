package net.github.dctime.screen;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Fullscreen multiline editor for {@code Config.PROMPT}/{@code Config.PROMPT_SCREENSHOT}, opened
 * from {@link TranslatorAdvancedConfigScreen}. Edits a local scratch string; only writes into the
 * shared {@link PendingTranslatorConfig} (never {@link net.github.dctime.Config} directly -- see
 * that class's lifecycle contract) when THIS screen's own Done is pressed. Cancel/ESC discards the
 * edit and returns to Advanced with the prompt untouched.
 * <p>
 * All positions are computed from {@code this.width}/{@code this.height}, not fixed pixel
 * constants, so it stays usable across window sizes / GUI scales.
 * <p>
 * NOT covered by any headless test -- see {@link TranslatorConfigScreen}'s javadoc for why.
 */
public class CustomPromptEditScreen extends Screen {

    private static final String P = MicrodaerysTranslatorClient.MODID + ".config.";

    private final Screen parent;
    private final PendingTranslatorConfig pending;
    private final boolean screenshotPrompt;
    private String workingValue;
    private MultiLineEditBox editBox;

    public CustomPromptEditScreen(Screen parent, PendingTranslatorConfig pending, boolean screenshotPrompt) {
        super(Component.translatable(screenshotPrompt ? P + "prompt_edit.screenshot_title" : P + "prompt_edit.title"));
        this.parent = parent;
        this.pending = pending;
        this.screenshotPrompt = screenshotPrompt;
        this.workingValue = screenshotPrompt ? pending.promptScreenshot : pending.prompt;
    }

    @Override
    protected void init() {
        int margin = 20;
        int footerHeight = 30;
        int textAreaTop = 40;

        editBox = new MultiLineEditBox(font, margin, textAreaTop, width - margin * 2, height - textAreaTop - footerHeight,
                Component.translatable(P + "prompt_edit.hint"), title);
        editBox.setCharacterLimit(8000);
        editBox.setValue(workingValue);
        editBox.setValueListener(v -> workingValue = v);
        addRenderableWidget(editBox);

        int buttonY = height - footerHeight + 5;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 - 155, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            if (screenshotPrompt) pending.promptScreenshot = workingValue; else pending.prompt = workingValue;
            onClose();
        }).bounds(width / 2 + 5, buttonY, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }

    /** Cancel/ESC: workingValue is discarded, pending is never touched. */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
