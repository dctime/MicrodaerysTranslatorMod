package net.github.dctime.screen;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.TargetLanguage;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.provider.ProviderInfo;
import net.github.dctime.libs.routing.ProviderMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple Settings: the first screen a player sees (Mods -> Microdaery's Translator -> Config, and
 * the Options-screen shortcut). Deliberately minimal -- everything a new player needs (routing
 * mode, language, feature toggles) with sensible defaults; everything else lives behind
 * {@link TranslatorAdvancedConfigScreen} or {@link ManageProvidersScreen}.
 * <p>
 * Owns the single {@link PendingTranslatorConfig} instance for this whole config-editing session
 * (see that class's javadoc for the lifecycle contract this depends on) and is the ONLY place
 * that ever calls {@link PendingTranslatorConfig#saveToConfig()} -- Cancel/ESC here, and anywhere
 * in {@link TranslatorAdvancedConfigScreen}, {@link CustomPromptEditScreen},
 * {@link ManageProvidersScreen}, or {@link ProviderDetailScreen}, just discards it.
 * <p>
 * <b>Multi-Provider Router refactor</b> (mailbox review rounds 023-032): the per-provider Service/
 * Model/API-Key/Custom-Provider fields that used to live directly on this screen are GONE --
 * multiple providers can be configured now, so a single "the" provider no longer makes sense on
 * the top-level screen (spec's own explicit "不要把 API Key 放 Simple 主頁了，因為現在有多家"). This
 * screen keeps only the {@code ProviderMode} selector plus a button into {@link
 * ManageProvidersScreen}, which lists every provider and opens {@link ProviderDetailScreen} for
 * whichever one the player picks. Test Connection moved with it -- it's per-provider now, so it
 * lives in {@link ProviderDetailScreen} instead of here.
 * <p>
 * NOT covered by any headless test: this whole file touches vanilla Minecraft GUI classes
 * (Screen/OptionsSubScreen/CycleButton/EditBox/...), which can't be constructed or rendered
 * without a running game -- verified by compiling and by manual in-game testing only.
 */
public class TranslatorConfigScreen extends OptionsSubScreen {

    private static final String P = MicrodaerysTranslatorClient.MODID + ".config.";

    private final PendingTranslatorConfig pending;

    private CycleButton<Boolean> followLanguageButtonRef;
    private CycleButton<String> targetLanguageButtonRef;

    /** Entry point used by {@code IConfigScreenFactory} -- the ONE place a fresh
     *  {@link PendingTranslatorConfig} is created (see its lifecycle contract). */
    public TranslatorConfigScreen(Screen parent) {
        this(parent, PendingTranslatorConfig.loadFromConfig());
    }

    /** Used when returning from Advanced/PromptEdit -- reuses the SAME pending instance, never
     *  re-snapshots from {@link Config}. */
    public TranslatorConfigScreen(Screen parent, PendingTranslatorConfig pending) {
        super(parent, Minecraft.getInstance().options, Component.translatable(P + "title"));
        this.pending = pending;
    }

    /** Matches NeoForge's own ConfigurationScreen.BIG_BUTTON_WIDTH (not reachable from here -- different class). */
    private static final int SECTION_WIDTH = 310;

    private static StringWidget sectionHeader(String key, Font font) {
        return new StringWidget(SECTION_WIDTH, Button.DEFAULT_HEIGHT,
                Component.translatable(key).withStyle(ChatFormatting.UNDERLINE), font).alignLeft();
    }

    /**
     * Re-populates {@code list} in place (clear then re-run {@link #addOptions()}) instead of
     * calling {@code Screen.rebuildWidgets()}. This deliberately mirrors NeoForge's own
     * {@code ConfigurationSectionScreen.rebuild()} rather than the more obvious-looking
     * {@code rebuildWidgets()}: {@code OptionsSubScreen.layout} (a {@code HeaderAndFooterLayout})
     * is a field created ONCE in the constructor, and {@code addContents()} calls
     * {@code layout.addToContents(new OptionsList(...))} -- {@code rebuildWidgets()} clears the
     * SCREEN's own widget-tracking lists and re-runs {@code init()}, but never clears {@code
     * layout}'s own internal content list, so each call accumulates ANOTHER whole {@code
     * OptionsList} (plus another title/footer) into the same layout instead of replacing the
     * previous one. The visible symptom (found by actually running the game, not by compiling):
     * every previous scroll/selection state's text stays rendered UNDER the new state, so clicking
     * anything leaves overlapping ghost text on screen permanently. Clearing and reusing the
     * existing {@code list} avoids ever creating a second {@code OptionsList} in the first place.
     */
    private void refreshOptions() {
        // The currently-focused widget (e.g. the API Key EditBox, mid-edit) is about to be
        // discarded by clear() below -- clear focus first so keyboard input can't keep targeting a
        // widget that no longer exists on screen (e.g. pressing Show while the EditBox has focus).
        setFocused(null);
        list.children().clear();
        addOptions();
    }

    @Override
    protected void addOptions() {
        list.addSmall(sectionHeader(P + "section.service", font), null);

        CycleButton<ProviderMode> providerModeButton = CycleButton.<ProviderMode>builder(
                        mode -> Component.translatable(P + "provider_mode." + mode.name().toLowerCase()))
                .withValues(ProviderMode.values())
                .withInitialValue(pending.providerMode)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(P + "provider_mode." + v.name().toLowerCase() + ".tooltip")))
                .create(Component.translatable(P + "provider_mode"), (btn, val) -> pending.providerMode = val);
        list.addSmall(new StringWidget(Component.translatable(P + "provider_mode"), font).alignLeft(), providerModeButton);

        Button manageProvidersButton = Button.builder(Component.translatable(P + "manage_providers"),
                b -> minecraft.setScreen(new ManageProvidersScreen(this, pending))).width(SECTION_WIDTH).build();
        list.addSmall(manageProvidersButton, null);

        list.addSmall(sectionHeader(P + "section.language", font), null);

        targetLanguageButtonRef = buildTargetLanguageButton();
        followLanguageButtonRef = CycleButton.onOffBuilder(pending.followGameLanguage)
                .displayOnlyValue() // this row already has its own StringWidget label to the left; without this the button ALSO prints "label: value" and the two overlap
                .withTooltip(v -> Tooltip.create(Component.translatable(P + "follow_game_language.tooltip")))
                .create(Component.translatable(P + "follow_game_language"), (btn, val) -> {
                    pending.followGameLanguage = val;
                    targetLanguageButtonRef.active = !val;
                });
        list.addSmall(new StringWidget(Component.translatable(P + "follow_game_language"), font).alignLeft(), followLanguageButtonRef);
        targetLanguageButtonRef.active = !pending.followGameLanguage;
        list.addSmall(new StringWidget(Component.translatable(P + "target_language"), font).alignLeft(), targetLanguageButtonRef);

        list.addSmall(sectionHeader(P + "section.features", font), null);
        list.addSmall(featureToggle(P + "feature.tooltip", pending.enableTooltipTranslation, v -> pending.enableTooltipTranslation = v),
                featureToggle(P + "feature.jade", pending.enableJadeTranslation, v -> pending.enableJadeTranslation = v));
        list.addSmall(featureToggle(P + "feature.ftbquests", pending.enableFtbQuestTranslation, v -> pending.enableFtbQuestTranslation = v),
                featureToggle(P + "feature.advancements", pending.enableAdvancementsTranslation, v -> pending.enableAdvancementsTranslation = v));
        list.addSmall(featureToggle(P + "feature.screenshot", pending.enableScreenshotTranslation, v -> pending.enableScreenshotTranslation = v), null);

        Button advancedButton = Button.builder(Component.translatable(P + "advanced_settings"),
                b -> minecraft.setScreen(new TranslatorAdvancedConfigScreen(this, pending))).width(SECTION_WIDTH).build();
        list.addSmall(advancedButton, null);
    }

    private CycleButton<String> buildTargetLanguageButton() {
        List<String> choices = new ArrayList<>(TargetLanguage.KNOWN_CODES);
        if (!choices.contains(pending.targetLanguage)) choices.add(pending.targetLanguage);
        return CycleButton.<String>builder(code -> TargetLanguage.KNOWN_CODES.contains(code)
                        ? Component.literal(TargetLanguage.displayName(code))
                        : Component.translatable(P + "target_language.custom", code))
                .withValues(choices)
                .withInitialValue(pending.targetLanguage)
                .displayOnlyValue()
                .create(Component.translatable(P + "target_language"), (btn, val) -> pending.targetLanguage = val);
    }

    /**
     * The recommended preset (index 0) is marked with a single "★ " character, not a translated
     * "(Recommended)" suffix -- see mailbox review #002 point H5. The 150px-wide model button
     * already clips long preset names (e.g. "Gemini 3.1 Flash Lite" loses "Gemini" off the front
     * when "(Recommended)" pushed the combined string past the button's width); a one-character,
     * untranslated marker costs almost nothing, whereas the localized suffix made every single
     * language's recommended-preset label longer, and several of the new ones (de_de, ru_ru) are
     * naturally longer than English/Chinese to begin with.
     * <p>
     * NOTE (mailbox review #002 point I3): U+2605 (★) isn't in Minecraft's default ASCII font
     * page, so it renders via the game's Unicode fallback font -- confirmed working in-game on
     * this mod's own dev/test setup, but "Force Unicode Font" being on/off, or a resource pack
     * that replaces the fallback font, could plausibly make it render as a missing-glyph box
     * instead. If a player ever reports a box here instead of a star, this is why.
     * <p>
     * Package-visible, not private: {@link ProviderDetailScreen}'s Model CycleButton reuses this
     * exact same "★ " + displayName presentation, now that per-provider model selection lives
     * there instead of on this screen.
     */
    static Component modelDisplayName(ProviderInfo provider, String modelId) {
        if (modelId.equals(PendingTranslatorConfig.CUSTOM_MODEL)) return Component.translatable(P + "model.custom");
        List<ProviderInfo.ModelPreset> models = provider.models();
        for (int i = 0; i < models.size(); i++) {
            ProviderInfo.ModelPreset preset = models.get(i);
            if (preset.modelId().equals(modelId)) {
                return i == 0 ? Component.literal("★ " + preset.displayName()) : Component.literal(preset.displayName());
            }
        }
        return Component.literal(modelId); // shouldn't happen: modelId always comes from this provider's own preset list or CUSTOM_MODEL
    }

    private CycleButton<Boolean> featureToggle(String labelKey, boolean initial, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .create(Component.translatable(labelKey), (btn, val) -> setter.accept(val));
    }

    @Override
    protected void addFooter() {
        LinearLayout row = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        row.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(Button.SMALL_WIDTH).build());
        row.addChild(Button.builder(CommonComponents.GUI_DONE, b -> handleDone()).width(Button.SMALL_WIDTH).build());
    }

    /** Cancel/ESC: never touches {@link Config}. {@link PendingTranslatorConfig} is simply discarded. */
    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }

    private void handleDone() {
        // The blank-Custom-Model / invalid-Custom-Base-URL blocking that used to live here (mailbox
        // review #002 point E2, round 016 point M3) moved to ProviderDetailScreen's own Back
        // handler -- those fields no longer live on this screen at all (Multi-Provider Router
        // refactor, this screen no longer edits any single provider directly). Router-level defense
        // in depth still exists regardless (TranslationRouter's per-candidate request-build try/
        // catch classifies a malformed request as BAD_REQUEST, job-scoped only, never a crash --
        // see that class's V1 fix), so a hand-edited or pre-refactor TOML value bypassing the GUI
        // check entirely still can't crash the render thread even without this screen's own check.
        int cacheSize = Translator.getCacheSize();
        if (pending.translationRelevantSettingsChanged() && cacheSize > 0) {
            BooleanConsumer onConfirm = yes -> {
                if (yes) Translator.clearCache(false);
                pending.saveToConfig();
                minecraft.setScreen(lastScreen);
            };
            minecraft.setScreen(new ConfirmScreen(onConfirm,
                    Component.translatable(P + "clear_cache_confirm.title"),
                    Component.translatable(P + "clear_cache_confirm.message", cacheSize)));
        } else {
            pending.saveToConfig();
            minecraft.setScreen(lastScreen);
        }
    }
}
