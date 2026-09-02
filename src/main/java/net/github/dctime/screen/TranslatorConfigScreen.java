package net.github.dctime.screen;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.TargetLanguage;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.TranslationConnectionTester;
import net.github.dctime.libs.BaseUrlUtil;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple Settings: the first screen a player sees (Mods -> Microdaery's Translator -> Config, and
 * the Options-screen shortcut). Deliberately minimal -- everything a new player needs (provider,
 * API key, language, feature toggles) with sensible defaults; everything else lives behind
 * {@link TranslatorAdvancedConfigScreen}.
 * <p>
 * Owns the single {@link PendingTranslatorConfig} instance for this whole config-editing session
 * (see that class's javadoc for the lifecycle contract this depends on) and is the ONLY place
 * that ever calls {@link PendingTranslatorConfig#saveToConfig()} -- Cancel/ESC here, and anywhere
 * in {@link TranslatorAdvancedConfigScreen} or {@link CustomPromptEditScreen}, just discards it.
 * <p>
 * NOT covered by any headless test: this whole file touches vanilla Minecraft GUI classes
 * (Screen/OptionsSubScreen/CycleButton/EditBox/...), which can't be constructed or rendered
 * without a running game -- verified by compiling and by manual in-game testing only.
 */
public class TranslatorConfigScreen extends OptionsSubScreen {

    private static final String P = MicrodaerysTranslatorClient.MODID + ".config.";

    private final PendingTranslatorConfig pending;
    private boolean blockedOnBlankCustomModel = false;
    private boolean blockedOnInvalidBaseUrl = false;

    private CycleButton<Boolean> followLanguageButtonRef;
    private CycleButton<String> targetLanguageButtonRef;
    private Button testConnectionButtonRef;
    private StringWidget testStatusWidgetRef;
    private boolean testInFlight = false;

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

        ProviderInfo provider = ProviderInfo.of(pending.endpoint);

        CycleButton<Config.EndPoint> serviceButton = CycleButton.<Config.EndPoint>builder(ep -> Component.translatable(ProviderInfo.of(ep).displayNameKey()))
                .withValues(ProviderInfo.ALL.stream().map(ProviderInfo::endpoint).toList())
                .withInitialValue(pending.endpoint)
                .displayOnlyValue()
                .create(Component.translatable(P + "service"), (btn, val) -> {
                    pending.onEndpointChanged(val);
                    refreshOptions();
                });
        list.addSmall(new StringWidget(Component.translatable(P + "service"), font).alignLeft(), serviceButton);

        if (pending.endpoint == Config.EndPoint.CUSTOM) {
            addCustomProviderFields();
        } else {
            List<String> modelChoices = new ArrayList<>();
            for (ProviderInfo.ModelPreset preset : provider.models()) modelChoices.add(preset.modelId());
            modelChoices.add(PendingTranslatorConfig.CUSTOM_MODEL);
            CycleButton<String> modelButton = CycleButton.<String>builder(id -> modelDisplayName(provider, id))
                    .withValues(modelChoices)
                    .withInitialValue(pending.modelSelection)
                    .displayOnlyValue()
                    .create(Component.translatable(P + "model"), (btn, val) -> {
                        pending.modelSelection = val;
                        refreshOptions();
                    });
            list.addSmall(new StringWidget(Component.translatable(P + "model"), font).alignLeft(), modelButton);

            if (pending.modelSelection.equals(PendingTranslatorConfig.CUSTOM_MODEL)) {
                EditBox customModelBox = new EditBox(font, 150, 20, Component.translatable(P + "model.custom_id"));
                customModelBox.setMaxLength(256);
                customModelBox.setValue(pending.customModel);
                customModelBox.setResponder(v -> pending.customModel = v);
                StringWidget customModelLabel = new StringWidget(Component.translatable(P + "model.custom_id"), font).alignLeft();
                // Only shown after a rejected Done attempt (see handleDone()) -- not a live "you might
                // regret this" nag while the player is still typing.
                if (blockedOnBlankCustomModel && pending.customModel.isBlank()) {
                    customModelLabel.setColor(0xFFFF5555);
                    customModelBox.setTextColor(0xFFFF5555);
                }
                list.addSmall(customModelLabel, customModelBox);
            }

            list.addSmall(new StringWidget(Component.translatable(P + "model_cache_note")
                    .withStyle(ChatFormatting.GRAY), font).alignLeft(), null);

            if (provider.requiresApiKey()) {
                addApiKeyFields();
            } else {
                list.addSmall(new StringWidget(Component.translatable(P + "api_key.ollama_note")
                        .withStyle(ChatFormatting.GRAY), font).alignLeft(), null);
            }
        }

        testConnectionButtonRef = Button.builder(Component.translatable(P + "test_connection"), b -> onTestConnectionPressed()).build();
        testConnectionButtonRef.active = !testInFlight;
        testStatusWidgetRef = new StringWidget(150, 20, Component.empty(), font);
        list.addSmall(testConnectionButtonRef, testStatusWidgetRef);
        list.addSmall(new StringWidget(Component.translatable(P + "test_connection.note")
                .withStyle(ChatFormatting.DARK_GRAY), font).alignLeft(), null);

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

    /** The standard masked API-key block, used by every built-in provider that
     *  {@link ProviderInfo#requiresApiKey()}. Extracted so {@link #addCustomProviderFields()} can
     *  reuse the exact same widget/sanitization/reveal behavior for its own key field. */
    private void addApiKeyFields() {
        EditBox apiKeyBox = new EditBox(font, 150, 20, Component.translatable(P + "api_key"));
        apiKeyBox.setMaxLength(512);
        apiKeyBox.setValue(pending.apiKey);
        // Sanitize on EVERY change, not just this mod's own Paste button below: EditBox has
        // its own native Ctrl+V handling that fires this same responder without ever going
        // through that button, and typing normally can't produce control characters anyway --
        // see PendingTranslatorConfig.sanitizeApiKey()'s javadoc for why this matters (a
        // trailing newline here crashes Test Connection's click handler synchronously). NOTE:
        // the EditBox's own displayed text is NOT rewritten back to the sanitized value here
        // (that would fight the box's own cursor/selection state) -- only pending.apiKey (what
        // Test Connection and Done actually use) is guaranteed clean; the visible box may
        // still show a raw pasted newline until the player retypes or the screen rebuilds.
        apiKeyBox.setResponder(v -> pending.apiKey = PendingTranslatorConfig.sanitizeApiKey(v));
        if (!pending.apiKeyRevealed) {
            apiKeyBox.setFormatter((text, index) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        }
        list.addSmall(new StringWidget(Component.translatable(P + "api_key"), font).alignLeft(), apiKeyBox);

        Button showHideButton = Button.builder(
                Component.translatable(pending.apiKeyRevealed ? P + "api_key.hide" : P + "api_key.show"),
                b -> {
                    pending.apiKeyRevealed = !pending.apiKeyRevealed;
                    refreshOptions();
                }).build();
        Button pasteButton = Button.builder(Component.translatable(P + "api_key.paste"), b -> {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null) pending.apiKey = PendingTranslatorConfig.sanitizeApiKey(clipboard);
            refreshOptions(); // also re-renders the EditBox with the now-sanitized value, unlike the native-paste path above
        }).build();
        list.addSmall(showHideButton, pasteButton);
    }

    /**
     * Custom Provider's fields -- shown INSTEAD of the standard Model CycleButton + API key block
     * (see the branch in {@link #addOptions()}), never alongside them. First-version scope per
     * spec: OpenAI-compatible chat completions only, Bearer/None auth, no arbitrary JSON
     * templating. Base URL/model are free text (there is nothing to curate a preset list from);
     * {@link PendingTranslatorConfig#customModel} doubles as this provider's plain model field --
     * {@code modelSelection} is always {@link PendingTranslatorConfig#CUSTOM_MODEL} for Custom
     * (see {@code ProviderInfo.of(CUSTOM).models()}, deliberately empty), so the existing blank-
     * model blocking logic in {@link #handleDone()} already covers this field with no extra code.
     */
    private void addCustomProviderFields() {
        EditBox nameBox = new EditBox(font, 150, 20, Component.translatable(P + "custom_provider.name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(pending.customProviderName);
        nameBox.setResponder(v -> pending.customProviderName = v);
        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.name"), font).alignLeft(), nameBox);

        EditBox baseUrlBox = new EditBox(font, 150, 20, Component.translatable(P + "custom_provider.base_url"));
        baseUrlBox.setMaxLength(512);
        baseUrlBox.setValue(pending.customProviderBaseUrl);
        baseUrlBox.setResponder(v -> pending.customProviderBaseUrl = v);
        StringWidget baseUrlLabel = new StringWidget(Component.translatable(P + "custom_provider.base_url"), font).alignLeft();
        // Only shown after a rejected Done attempt (see handleDone()) -- same convention as blank
        // Custom Model below; not a live nag while the player is still typing the URL.
        if (blockedOnInvalidBaseUrl && !BaseUrlUtil.isValid(pending.customProviderBaseUrl)) {
            baseUrlLabel.setColor(0xFFFF5555);
            baseUrlBox.setTextColor(0xFFFF5555);
        }
        list.addSmall(baseUrlLabel, baseUrlBox);

        CycleButton<AuthMode> authButton = CycleButton.<AuthMode>builder(mode -> Component.translatable(
                        mode == AuthMode.NONE ? P + "custom_provider.authentication.none" : P + "custom_provider.authentication.bearer"))
                .withValues(AuthMode.BEARER, AuthMode.NONE)
                .withInitialValue(pending.customProviderAuthMode)
                .displayOnlyValue()
                .create(Component.translatable(P + "custom_provider.authentication"), (btn, val) -> {
                    pending.customProviderAuthMode = val;
                    refreshOptions();
                });
        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.authentication"), font).alignLeft(), authButton);

        addApiKeyFields();

        EditBox modelBox = new EditBox(font, 150, 20, Component.translatable(P + "model.custom_id"));
        modelBox.setMaxLength(256);
        modelBox.setValue(pending.customModel);
        modelBox.setResponder(v -> pending.customModel = v);
        StringWidget modelLabel = new StringWidget(Component.translatable(P + "model.custom_id"), font).alignLeft();
        if (blockedOnBlankCustomModel && pending.customModel.isBlank()) {
            modelLabel.setColor(0xFFFF5555);
            modelBox.setTextColor(0xFFFF5555);
        }
        list.addSmall(modelLabel, modelBox);

        CycleButton<Boolean> visionButton = CycleButton.onOffBuilder(pending.customProviderSupportsVision)
                .displayOnlyValue()
                .create(Component.translatable(P + "custom_provider.supports_images"),
                        (btn, val) -> pending.customProviderSupportsVision = val);
        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.supports_images"), font).alignLeft(), visionButton);

        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.privacy_note")
                .withStyle(ChatFormatting.GRAY), font).alignLeft(), null);
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
     */
    private static Component modelDisplayName(ProviderInfo provider, String modelId) {
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

    private void onTestConnectionPressed() {
        testInFlight = true;
        testConnectionButtonRef.active = false;
        testStatusWidgetRef.setMessage(Component.translatable(P + "test_connection.testing"));
        // Uses PENDING values, not yet saved -- see the class javadoc's acceptance test 6 (Test
        // Connection doesn't need Done first). Custom Provider's base URL/auth mode are only
        // meaningful for that endpoint; the tester ignores them for every other provider.
        TranslationConnectionTester.test(pending.endpoint, pending.apiKey, pending.resolvedModel(),
                pending.customProviderBaseUrl, pending.customProviderAuthMode, result ->
                Minecraft.getInstance().execute(() -> {
                    // the player may have closed/replaced this screen while the request was in
                    // flight -- don't touch widgets that no longer belong to the visible screen.
                    if (Minecraft.getInstance().screen != this) return;
                    testInFlight = false;
                    testConnectionButtonRef.active = true;
                    testStatusWidgetRef.setMessage(describeResult(result));
                }));
    }

    private Component describeResult(TranslationConnectionTester.Result result) {
        return switch (result.status()) {
            case OK -> {
                MutableComponent connected = Component.translatable(P + "test_connection.connected").withStyle(ChatFormatting.GREEN);
                if (result.modelFound()) yield connected;
                MutableComponent hint = Component.translatable(P + "test_connection.model_not_found", pending.resolvedModel())
                        .withStyle(ChatFormatting.YELLOW);
                yield connected.append(Component.literal(" - ")).append(hint);
            }
            case INVALID_KEY -> Component.translatable(P + "test_connection.invalid_key").withStyle(ChatFormatting.RED);
            case RATE_LIMITED -> Component.translatable(P + "test_connection.rate_limited").withStyle(ChatFormatting.YELLOW);
            case CANNOT_CONNECT -> Component.translatable(P + "test_connection.cannot_connect").withStyle(ChatFormatting.RED);
            case HTTP_ERROR -> Component.translatable(P + "test_connection.http_error", result.httpStatusCode()).withStyle(ChatFormatting.RED);
            case INVALID_BASE_URL -> Component.translatable(P + "test_connection.invalid_base_url").withStyle(ChatFormatting.RED);
        };
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
        // Blocked, not silently defaulted: the player explicitly chose Custom, so quietly
        // substituting something else on their behalf would be more confusing than making them
        // fix it -- see mailbox review #002 point E2 (an empty model_name compiles a broken
        // request URL/body for every provider, and Test Connection's list-models check can't catch
        // this since it doesn't depend on model_name at all).
        if (pending.resolvedModel().isBlank()) {
            blockedOnBlankCustomModel = true;
            refreshOptions();
            return;
        }

        // Same blocking treatment for Custom Provider's Base URL, added per mailbox review round
        // 016 point M3: a blank or malformed Base URL builds a syntactically-legal RELATIVE URI
        // (URI.create doesn't reject it), which HttpRequest.newBuilder().uri(...) then rejects --
        // synchronously, on whatever thread asked for a request. This is the SAME failure shape as
        // E1 (API key with an embedded newline) and just as easy to trigger: pick Custom Provider,
        // leave Base URL empty, press Done. OpenAiCompatibleAdapter still independently guards
        // against this at request-build time too (a hand-edited or pre-this-fix TOML value could
        // bypass this GUI check entirely) -- this block is the first, clearest line of defense, not
        // the only one.
        if (pending.endpoint == Config.EndPoint.CUSTOM && !BaseUrlUtil.isValid(pending.customProviderBaseUrl)) {
            blockedOnInvalidBaseUrl = true;
            refreshOptions();
            return;
        }

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
