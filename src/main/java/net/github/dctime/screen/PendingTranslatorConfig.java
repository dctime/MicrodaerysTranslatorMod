package net.github.dctime.screen;

import net.github.dctime.Config;
import net.github.dctime.libs.ApiKeyUtil;
import net.github.dctime.libs.BaseUrlUtil;
import net.github.dctime.libs.ModelIdUtil;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderConfigResolver;
import net.github.dctime.libs.provider.ProviderInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scratch copy of every {@link Config} value the Simple/Advanced screens let the player edit.
 * Nothing is written back to {@link Config} until {@link #saveToConfig()} is called (only
 * {@code TranslatorConfigScreen}'s Done button does that); Cancel/ESC anywhere just discards this
 * object.
 * <p>
 * Lifecycle contract (see mailbox review #002 point A): exactly ONE instance is created, at the
 * {@code IConfigScreenFactory} entry point ({@code TranslatorConfigScreen}'s single-arg
 * constructor calls {@link #loadFromConfig()}). Every other screen (Advanced, the custom-prompt
 * editor) receives that SAME instance through a constructor parameter and must never construct
 * its own or re-snapshot from {@link Config}. {@code Screen.rebuildWidgets()} (used whenever the
 * GUI needs to change which widgets are shown, e.g. switching provider changes the model list)
 * re-runs {@code init()}, so a screen that snapshotted in its own {@code init()} would silently
 * reset {@link #translationRelevantSettingsChanged()} to always-false after any rebuild -- no
 * exception, no crash, the "clear cache?" confirmation would just never appear again.
 * <p>
 * <b>Per-provider persistence</b> (added for the 11-provider expansion): each of the 11
 * {@link Config.EndPoint}s remembers its OWN {@code apiKey}/model (Custom Provider additionally
 * its name/base URL/auth mode/vision toggle), keyed in {@link #perProvider}. The fields
 * {@link #apiKey}, {@link #modelSelection}, {@link #customModel}, and the {@code customProvider*}
 * fields below are always a live view of the CURRENTLY ACTIVE endpoint's entry -- simple direct
 * binding for widgets, same shape the pre-refactor code already used for {@code modelSelection}/
 * {@code customModel} alone (this just extends that same pattern to cover API key and Custom
 * Provider's extra fields too, since those used to be single flat values shared by whichever
 * provider was active -- the actual bug this persistence work exists to fix). {@link #perProvider}
 * is populated EAGERLY for all 11 endpoints in {@link #loadFromConfig()} (each resolved through
 * {@link ProviderConfigResolver}, which also applies the one-time legacy-migration fallback for
 * whichever endpoint {@code Config.ENDPOINT_CONFIG} currently names), not lazily as endpoints are
 * first visited -- so switching Service repeatedly always shows each provider's real saved values,
 * including ones never visited this session, and {@link #saveToConfig()} can simply write every
 * entry without tracking "was this one touched".
 */
public class PendingTranslatorConfig {

    /** Sentinel model selection meaning "use customModel instead of any preset". Not a real model
     *  id any provider would ever ship, so it can't collide with one. Also what Custom Provider
     *  (which has no presets at all) always uses -- its "preset list" is empty, so this sentinel is
     *  the only possible selection, and customModel is simply its free-typed model field. */
    public static final String CUSTOM_MODEL = "__custom__";

    public Config.EndPoint endpoint;
    /** One of the current provider's ModelPreset ids, or {@link #CUSTOM_MODEL}. */
    public String modelSelection;
    /** The actual model id in effect when modelSelection == CUSTOM_MODEL (or always, for Custom
     *  Provider, which has no presets). */
    public String customModel;
    public String apiKey;
    public boolean apiKeyRevealed;

    // Custom Provider-only fields -- live view of the CUSTOM endpoint's entry, meaningful only
    // while endpoint == Config.EndPoint.CUSTOM (same "live view of the active entry" convention as
    // apiKey/modelSelection/customModel above).
    public String customProviderName;
    public String customProviderBaseUrl;
    public AuthMode customProviderAuthMode;
    public boolean customProviderSupportsVision;

    public boolean followGameLanguage;
    public String targetLanguage;

    public boolean enableTooltipTranslation;
    public boolean enableJadeTranslation;
    public boolean enableFtbQuestTranslation;
    public boolean enableAdvancementsTranslation;
    public boolean enableScreenshotTranslation;

    public boolean enableIcon;
    public boolean enablePretranslateContainers;
    public String prompt;
    public String promptScreenshot;
    public boolean enableTranslatingAnimation;
    public int timeoutDuration;
    public int maxRequestsPerMinute;

    /** Per-provider scratch state for every {@link Config.EndPoint} INCLUDING the currently active
     *  one (kept in sync with the flat fields above by {@link #syncActiveIntoMap()}/
     *  {@link #loadActiveFromMap()}, called from {@link #onEndpointChanged}). */
    private final Map<Config.EndPoint, ProviderPendingState> perProvider = new EnumMap<>(Config.EndPoint.class);

    private static final class ProviderPendingState {
        String modelSelection = CUSTOM_MODEL;
        String customModel = "";
        String apiKey = "";
        String customProviderName = "";
        String customProviderBaseUrl = "";
        AuthMode customProviderAuthMode = AuthMode.BEARER;
        boolean customProviderSupportsVision = false;
    }

    // Snapshotted once at construction (see class javadoc) -- compared against the live pending
    // values in translationRelevantSettingsChanged() to decide whether Done should offer to clear
    // the cache (mailbox review #002 point 1).
    private final Config.EndPoint originalEndpoint;
    private final String originalModel;
    private final String originalPrompt;
    private final String originalPromptScreenshot;

    private PendingTranslatorConfig(Config.EndPoint endpoint,
                                     boolean followGameLanguage, String targetLanguage,
                                     boolean tooltip, boolean jade, boolean ftb, boolean advancements, boolean screenshot,
                                     boolean icon, boolean pretranslate, String prompt, String promptScreenshot,
                                     boolean animation, int timeout, int rpm) {
        this.followGameLanguage = followGameLanguage;
        this.targetLanguage = targetLanguage;
        this.enableTooltipTranslation = tooltip;
        this.enableJadeTranslation = jade;
        this.enableFtbQuestTranslation = ftb;
        this.enableAdvancementsTranslation = advancements;
        this.enableScreenshotTranslation = screenshot;
        this.enableIcon = icon;
        this.enablePretranslateContainers = pretranslate;
        this.prompt = prompt;
        this.promptScreenshot = promptScreenshot;
        this.enableTranslatingAnimation = animation;
        this.timeoutDuration = timeout;
        this.maxRequestsPerMinute = rpm;

        for (Config.EndPoint ep : Config.EndPoint.values()) {
            perProvider.put(ep, freshStateFor(ep));
        }

        this.endpoint = endpoint;
        loadActiveFromMap();

        this.originalEndpoint = endpoint;
        this.originalModel = resolvedModel();
        this.originalPrompt = prompt;
        this.originalPromptScreenshot = promptScreenshot;
    }

    /** Resolves one endpoint's saved settings fresh from {@link Config} (applying legacy
     *  migration for whichever endpoint is currently active, via {@link ProviderConfigResolver}) --
     *  never writes anything back. */
    private static ProviderPendingState freshStateFor(Config.EndPoint endpoint) {
        ProviderConfigResolver.ResolvedProviderConfig resolved = ProviderConfigResolver.resolve(endpoint);
        ProviderPendingState state = new ProviderPendingState();
        state.apiKey = resolved.apiKey();

        if (endpoint == Config.EndPoint.CUSTOM) {
            state.customModel = resolved.model();
            state.modelSelection = CUSTOM_MODEL;
            state.customProviderName = Config.CUSTOM_PROVIDER_NAME.get();
            state.customProviderBaseUrl = Config.CUSTOM_PROVIDER_BASE_URL.get();
            state.customProviderAuthMode = "NONE".equalsIgnoreCase(Config.CUSTOM_PROVIDER_AUTH_MODE.get())
                    ? AuthMode.NONE : AuthMode.BEARER;
            state.customProviderSupportsVision = Config.CUSTOM_PROVIDER_SUPPORTS_VISION.get();
            return state;
        }

        // Never overwrite an unrecognized model with a preset (backward compatibility with
        // whatever's already saved): only pick a preset selection if the saved model is
        // byte-identical to one of THIS provider's presets; anything else -- a typo, a model this
        // mod doesn't list, or blank (never configured before) -- is Custom Model, falling back to
        // the recommended preset (index 0) only when genuinely blank.
        List<ProviderInfo.ModelPreset> presets = ProviderInfo.of(endpoint).models();
        String rawModel = resolved.model();
        String model = (rawModel.isBlank() && !presets.isEmpty()) ? presets.get(0).modelId() : rawModel;
        state.customModel = model;
        state.modelSelection = presets.stream().anyMatch(p -> p.modelId().equals(model)) ? model : CUSTOM_MODEL;
        return state;
    }

    public static PendingTranslatorConfig loadFromConfig() {
        return new PendingTranslatorConfig(
                Config.ENDPOINT_CONFIG.get(),
                Config.FOLLOW_GAME_LANGUAGE.get(), Config.TARGET_LANGUAGE.get(),
                Config.ENABLE_TOOLTIP_TRANSLATION.get(), Config.ENABLE_JADE_CONFIG.get(),
                Config.ENABLE_FTB_QUEST_TRANSLATION.get(), Config.ENABLE_ADVANCEMENTS_CONFIG.get(),
                Config.ENABLE_SCREENSHOT_CONFIG.get(),
                Config.ENABLE_ICON_CONFIG.get(), Config.ENABLE_PRETRANSLATE_CONTAINERS.get(),
                Config.PROMPT.get(), Config.PROMPT_SCREENSHOT.get(),
                Config.ENABLE_TRANSLATING_ANIMATION_CONFIG.get(),
                Config.TIMEOUT_DURATION_CONFIG.get(), Config.MAX_REQUESTS_PER_MINUTE.get()
        );
    }

    /** The model id that would actually be sent to the provider right now. */
    public String resolvedModel() {
        return modelSelection.equals(CUSTOM_MODEL) ? customModel : modelSelection;
    }

    private void syncActiveIntoMap() {
        ProviderPendingState state = perProvider.get(this.endpoint);
        state.modelSelection = this.modelSelection;
        state.customModel = this.customModel;
        state.apiKey = this.apiKey;
        state.customProviderName = this.customProviderName;
        state.customProviderBaseUrl = this.customProviderBaseUrl;
        state.customProviderAuthMode = this.customProviderAuthMode;
        state.customProviderSupportsVision = this.customProviderSupportsVision;
    }

    private void loadActiveFromMap() {
        ProviderPendingState state = perProvider.get(this.endpoint);
        this.modelSelection = state.modelSelection;
        this.customModel = state.customModel;
        this.apiKey = state.apiKey;
        this.customProviderName = state.customProviderName;
        this.customProviderBaseUrl = state.customProviderBaseUrl;
        this.customProviderAuthMode = state.customProviderAuthMode;
        this.customProviderSupportsVision = state.customProviderSupportsVision;
    }

    /**
     * Call when the player switches Service in the GUI.
     * <p>
     * Saves everything about the endpoint being LEFT into {@link #perProvider} (model AND api key
     * AND, for Custom, its extra fields -- extending what used to be model-only), then loads
     * whatever is on record for the NEW endpoint -- which, since {@link #perProvider} is populated
     * for all 11 endpoints up front (see {@link #loadFromConfig()}), is always that provider's real
     * saved values, not just "whatever was selected earlier this session".
     */
    public void onEndpointChanged(Config.EndPoint newEndpoint) {
        syncActiveIntoMap();
        this.endpoint = newEndpoint;
        loadActiveFromMap();
    }

    /**
     * True if endpoint/model/prompt/prompt_screenshot differ from what was loaded when this
     * screen was opened -- exactly the values that change what a cached translation would have
     * looked like, so a change here (and only here) is what should prompt "clear the cache?".
     * Custom Provider's base URL/auth mode are deliberately NOT included -- same scope the
     * pre-refactor version had (provider/model/prompt only), kept narrow on purpose per the spec
     * (the translation cache's key stays (language, text) with no provider dimension at all).
     */
    public boolean translationRelevantSettingsChanged() {
        return endpoint != originalEndpoint
                || !resolvedModel().equals(originalModel)
                || !prompt.equals(originalPrompt)
                || !promptScreenshot.equals(originalPromptScreenshot);
    }

    /**
     * Strips whitespace/control characters (including newlines) from a pasted or typed API key.
     * Delegates to {@link ApiKeyUtil#sanitize} -- kept as a static method here too (rather than
     * requiring every call site to import a different class) since this is the one already wired
     * into every EditBox responder in {@code TranslatorConfigScreen}.
     */
    public static String sanitizeApiKey(String rawApiKey) {
        return ApiKeyUtil.sanitize(rawApiKey);
    }

    private static String resolvedModelOf(ProviderPendingState state) {
        return state.modelSelection.equals(CUSTOM_MODEL) ? state.customModel : state.modelSelection;
    }

    public void saveToConfig() {
        syncActiveIntoMap(); // capture the currently-active endpoint's live edits before iterating

        Config.ENDPOINT_CONFIG.set(endpoint);

        for (Map.Entry<Config.EndPoint, ProviderPendingState> entry : perProvider.entrySet()) {
            Config.EndPoint ep = entry.getKey();
            ProviderPendingState state = entry.getValue();
            if (ep == Config.EndPoint.CUSTOM) {
                Config.CUSTOM_PROVIDER_NAME.set(state.customProviderName);
                Config.CUSTOM_PROVIDER_BASE_URL.set(BaseUrlUtil.normalize(state.customProviderBaseUrl));
                Config.CUSTOM_PROVIDER_API_KEY.set(ApiKeyUtil.sanitize(state.apiKey));
                Config.CUSTOM_PROVIDER_MODEL.set(ModelIdUtil.sanitize(resolvedModelOf(state)));
                Config.CUSTOM_PROVIDER_AUTH_MODE.set(state.customProviderAuthMode.name());
                Config.CUSTOM_PROVIDER_SUPPORTS_VISION.set(state.customProviderSupportsVision);
            } else {
                Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(ep);
                if (keys.apiKey() != null) keys.apiKey().set(ApiKeyUtil.sanitize(state.apiKey));
                keys.model().set(ModelIdUtil.sanitize(resolvedModelOf(state)));
            }
        }

        // NOT dead/redundant writes -- do not delete these two lines during a future cleanup pass
        // just because Translator's new code path never reads them again (mailbox review round
        // 020, point Q2's explicit request to document this). They are the ONLY thing that keeps
        // a downgrade to a pre-11-provider build usable for a player whose active provider at the
        // time was one of the original three (Google/Ollama/Mistral): NeoForge's
        // ModConfigSpec.correct() resets Config.ENDPOINT_CONFIG to its default (MISTRAL) on
        // downgrade whenever the saved value is one of the 8 new EndPoint constants the old code's
        // enum doesn't have (confirmed against NeoForge 21.1.4's own decompiled source, not
        // assumed), and separately deletes every per-provider providers.* TOML section outright
        // (same method, the unspecified-key removal step) since the old ModConfigSpec doesn't
        // define them. These two flat legacy keys are the one place still written that the OLD
        // code's spec also still defines, so they survive both of those. Without them, EVERY
        // downgrade -- not just a downgrade away from one of the 8 new providers -- would strand
        // the player with a blank API key. Remove this comment only if these two lines are removed
        // with it, never leave them looking like leftover cruft.
        Config.API_KEY.set(ApiKeyUtil.sanitize(apiKey));
        Config.MODEL_NAME.set(ModelIdUtil.sanitize(resolvedModel()));

        Config.FOLLOW_GAME_LANGUAGE.set(followGameLanguage);
        Config.TARGET_LANGUAGE.set(targetLanguage);
        Config.ENABLE_TOOLTIP_TRANSLATION.set(enableTooltipTranslation);
        Config.ENABLE_JADE_CONFIG.set(enableJadeTranslation);
        Config.ENABLE_FTB_QUEST_TRANSLATION.set(enableFtbQuestTranslation);
        Config.ENABLE_ADVANCEMENTS_CONFIG.set(enableAdvancementsTranslation);
        Config.ENABLE_SCREENSHOT_CONFIG.set(enableScreenshotTranslation);
        Config.ENABLE_ICON_CONFIG.set(enableIcon);
        Config.ENABLE_PRETRANSLATE_CONTAINERS.set(enablePretranslateContainers);
        Config.PROMPT.set(prompt);
        Config.PROMPT_SCREENSHOT.set(promptScreenshot);
        Config.ENABLE_TRANSLATING_ANIMATION_CONFIG.set(enableTranslatingAnimation);
        Config.TIMEOUT_DURATION_CONFIG.set(timeoutDuration);
        Config.MAX_REQUESTS_PER_MINUTE.set(maxRequestsPerMinute);
        Config.save();
    }
}
