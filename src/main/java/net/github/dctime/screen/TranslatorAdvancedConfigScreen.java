package net.github.dctime.screen;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.PromptTemplates;
import net.github.dctime.libs.Translator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Settings: everything a new player doesn't need on day one. Reached only from
 * {@link TranslatorConfigScreen}'s "Advanced Settings" button, and its footer's Done button
 * (the default {@code OptionsSubScreen} one, not overridden here) is really just "back" -- it
 * never touches {@link net.github.dctime.Config}. Only {@link TranslatorConfigScreen}'s own Done
 * button ever commits {@link PendingTranslatorConfig} (see that class's lifecycle contract);
 * bouncing between Simple and Advanced any number of times before that is free.
 * <p>
 * NOT covered by any headless test -- see {@link TranslatorConfigScreen}'s javadoc for why.
 */
public class TranslatorAdvancedConfigScreen extends OptionsSubScreen {

    private static final String P = MicrodaerysTranslatorClient.MODID + ".config.";
    private static final Integer CUSTOM_INT = Integer.MIN_VALUE;
    private static final List<Integer> TIMEOUT_PRESETS = List.of(10, 15, 30, 45, 60, 90);
    private static final List<Integer> RPM_PRESETS = List.of(5, 10, 15, 30, 60, 120);

    private final PendingTranslatorConfig pending;
    private Button clearCacheButtonRef;

    public TranslatorAdvancedConfigScreen(Screen parent, PendingTranslatorConfig pending) {
        super(parent, Minecraft.getInstance().options, Component.translatable(P + "advanced_settings"));
        this.pending = pending;
    }

    /** See {@link TranslatorConfigScreen#refreshOptions()} -- same reasoning, same fix (clear and
     *  re-run addOptions() in place rather than Screen.rebuildWidgets(), which accumulates a new
     *  OptionsList into this screen's HeaderAndFooterLayout on every call instead of replacing it). */
    private void refreshOptions() {
        // See TranslatorConfigScreen#refreshOptions() -- clear focus before discarding whatever
        // widget currently holds it, so keyboard input can't keep targeting a widget that's about
        // to stop existing on screen.
        setFocused(null);
        list.children().clear();
        addOptions();
    }

    @Override
    protected void addOptions() {
        list.addSmall(sectionHeader(P + "section.translation"), null);

        list.addSmall(label(P + "include_icon"), toggle(P + "include_icon", pending.enableIcon,
                v -> pending.enableIcon = v));
        list.addSmall(label(P + "pretranslate_containers"), toggle(P + "pretranslate_containers",
                pending.enablePretranslateContainers, v -> pending.enablePretranslateContainers = v));

        boolean promptIsBuiltin = PromptTemplates.isBlankOrLegacyDefault(pending.prompt);
        list.addSmall(label(P + "custom_prompt"),
                new StringWidget(Component.translatable(promptIsBuiltin ? P + "custom_prompt.builtin" : P + "custom_prompt.custom"), font).alignLeft());
        Button editPromptButton = Button.builder(Component.translatable(P + "edit_custom_prompt"),
                b -> minecraft.setScreen(new CustomPromptEditScreen(this, pending, false))).build();
        Button resetPromptButton = Button.builder(Component.translatable(P + "reset_to_builtin"), b -> {
            pending.prompt = "";
            refreshOptions();
        }).build();
        resetPromptButton.active = !promptIsBuiltin;
        list.addSmall(editPromptButton, resetPromptButton);

        boolean screenshotPromptIsBuiltin = PromptTemplates.isBlankOrLegacyScreenshotDefault(pending.promptScreenshot);
        Button editScreenshotPromptButton = Button.builder(Component.translatable(P + "edit_screenshot_prompt"),
                b -> minecraft.setScreen(new CustomPromptEditScreen(this, pending, true))).build();
        Button resetScreenshotPromptButton = Button.builder(Component.translatable(P + "reset_to_builtin"), b -> {
            pending.promptScreenshot = "";
            refreshOptions();
        }).build();
        resetScreenshotPromptButton.active = !screenshotPromptIsBuiltin;
        list.addSmall(editScreenshotPromptButton, resetScreenshotPromptButton);

        list.addSmall(sectionHeader(P + "section.interface"), null);
        list.addSmall(label(P + "translating_animation"), toggle(P + "translating_animation",
                pending.enableTranslatingAnimation, v -> pending.enableTranslatingAnimation = v));

        list.addSmall(sectionHeader(P + "section.network"), null);
        addTimeoutRow();
        addRpmRow();

        list.addSmall(sectionHeader(P + "section.cache"), null);
        clearCacheButtonRef = Button.builder(Component.translatable(P + "clear_cache", Translator.getCacheSize()), b -> {
            Translator.clearCache(false);
            clearCacheButtonRef.setMessage(Component.translatable(P + "clear_cache", Translator.getCacheSize()));
        }).width(SECTION_WIDTH).build();
        list.addSmall(clearCacheButtonRef, null);
    }

    private void addTimeoutRow() {
        List<Integer> choices = new ArrayList<>(TIMEOUT_PRESETS);
        choices.add(CUSTOM_INT);
        int selection = TIMEOUT_PRESETS.contains(pending.timeoutDuration) ? pending.timeoutDuration : CUSTOM_INT;

        CycleButton<Integer> timeoutButton = CycleButton.<Integer>builder(v -> v.equals(CUSTOM_INT)
                        ? Component.translatable(P + "timeout.custom", pending.timeoutDuration)
                        : Component.translatable(P + "timeout.seconds", v))
                .withValues(choices)
                .withInitialValue(selection)
                .displayOnlyValue()
                .create(Component.translatable(P + "timeout"), (btn, val) -> {
                    if (!val.equals(CUSTOM_INT)) pending.timeoutDuration = val;
                    refreshOptions();
                });
        list.addSmall(label(P + "timeout"), timeoutButton);

        if (selection == CUSTOM_INT) {
            EditBox timeoutBox = new EditBox(font, 150, 20, Component.translatable(P + "timeout"));
            timeoutBox.setMaxLength(9);
            timeoutBox.setValue(String.valueOf(pending.timeoutDuration));
            timeoutBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,9}"));
            timeoutBox.setResponder(s -> {
                try {
                    int v = Integer.parseInt(s);
                    if (v > 0) pending.timeoutDuration = v;
                } catch (NumberFormatException ignored) {
                    // partial/empty input while typing -- keep the last valid value, don't reject the keystroke
                }
            });
            list.addSmall(new StringWidget(Component.empty(), font), timeoutBox);
        }
    }

    private void addRpmRow() {
        List<Integer> choices = new ArrayList<>(RPM_PRESETS);
        choices.add(CUSTOM_INT);
        int selection = RPM_PRESETS.contains(pending.maxRequestsPerMinute) ? pending.maxRequestsPerMinute : CUSTOM_INT;

        CycleButton<Integer> rpmButton = CycleButton.<Integer>builder(v -> v.equals(CUSTOM_INT)
                        ? Component.translatable(P + "rpm.custom", pending.maxRequestsPerMinute)
                        : Component.literal(String.valueOf(v)))
                .withValues(choices)
                .withInitialValue(selection)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(P + "rpm.tooltip")))
                .create(Component.translatable(P + "rpm"), (btn, val) -> {
                    if (!val.equals(CUSTOM_INT)) pending.maxRequestsPerMinute = val;
                    refreshOptions();
                });
        list.addSmall(label(P + "rpm"), rpmButton);

        if (selection == CUSTOM_INT) {
            EditBox rpmBox = new EditBox(font, 150, 20, Component.translatable(P + "rpm"));
            rpmBox.setMaxLength(9);
            rpmBox.setValue(String.valueOf(pending.maxRequestsPerMinute));
            rpmBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,9}"));
            rpmBox.setResponder(s -> {
                try {
                    int v = Integer.parseInt(s);
                    // Config.MAX_REQUESTS_PER_MINUTE.defineInRange(1, Integer.MAX_VALUE) -- must
                    // stay >= 1, or saveToConfig()'s ModConfigSpec.IntValue.set() would reject it.
                    if (v >= 1) pending.maxRequestsPerMinute = v;
                } catch (NumberFormatException ignored) {
                    // partial/empty input while typing
                }
            });
            list.addSmall(new StringWidget(Component.empty(), font), rpmBox);
        }
    }

    private StringWidget label(String key) {
        return new StringWidget(Component.translatable(key), font).alignLeft();
    }

    private static final int SECTION_WIDTH = 310;

    private StringWidget sectionHeader(String key) {
        return new StringWidget(SECTION_WIDTH, Button.DEFAULT_HEIGHT, Component.translatable(key).withStyle(ChatFormatting.UNDERLINE), font).alignLeft();
    }

    private CycleButton<Boolean> toggle(String tooltipKey, boolean initial, java.util.function.Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .displayOnlyValue() // paired with a separate label() StringWidget in every call site -- without this the button ALSO prints "label: value" and the two overlap
                .withTooltip(v -> Tooltip.create(Component.translatable(tooltipKey + ".tooltip")))
                .create(Component.translatable(tooltipKey), (btn, val) -> setter.accept(val));
    }
}
