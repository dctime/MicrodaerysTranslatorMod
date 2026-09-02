package net.github.dctime.screen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.ApiKeyUtil;
import net.github.dctime.libs.BaseUrlUtil;
import net.github.dctime.libs.ModelIdUtil;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderConfigResolver;
import net.github.dctime.libs.provider.ProviderInfo;
import net.github.dctime.libs.routing.ProviderMode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scratch copy of every {@link Config} value the Simple/Advanced/Manage-Providers/Provider-Detail
 * screens let the player edit. Nothing is written back to {@link Config} until {@link
 * #saveToConfig()} is called (only {@code TranslatorConfigScreen}'s Done button does that);
 * Cancel/ESC/Back anywhere else just discards this object (or, for a single field, discards that
 * field's edit -- see each screen's own javadoc).
 * <p>
 * Lifecycle contract (see mailbox review #002 point A): exactly ONE instance is created, at the
 * {@code IConfigScreenFactory} entry point ({@code TranslatorConfigScreen}'s single-arg
 * constructor calls {@link #loadFromConfig()}). Every other screen (Advanced, the custom-prompt
 * editor, Manage Providers, Provider Detail) receives that SAME instance through a constructor
 * parameter and must never construct its own or re-snapshot from {@link Config}.
 * <p>
 * <b>Per-provider persistence, by EXPLICIT endpoint</b> (mailbox review round 032, point Y1's
 * sibling design fix): every {@link Config.EndPoint}'s api key/model/(Custom's extra fields)/
 * enabled/priority/rpm lives in {@link #perProvider}, read and written through accessor methods
 * that take an explicit {@code Config.EndPoint} parameter -- e.g. {@link #getProviderApiKey},
 * {@link #setProviderApiKey}. There is deliberately NO "live view of the currently active
 * endpoint" binding any more (the pre-Router version, and this refactor's own first draft, had
 * top-level fields like {@code apiKey}/{@code modelSelection} that always meant "whichever
 * provider {@link #endpoint} currently names", synced via an {@code onEndpointChanged} call) --
 * that design conflated two DIFFERENT things once multiple providers can be configured at once:
 * "which provider is SINGLE mode's target" ({@link #endpoint} itself) versus "which provider's
 * fields {@link ProviderDetailScreen} happens to be showing right now" (any provider at all,
 * chosen from {@link ManageProvidersScreen}'s list, completely independent of SINGLE mode's
 * target). Reusing the old sync-on-switch pattern for Provider Detail would have silently changed
 * {@link #endpoint} (and therefore which provider SINGLE mode uses) merely by opening Provider
 * Detail to look at a DIFFERENT provider's RPM or status -- a real correctness bug, not a style
 * preference, caught while designing the GUI layer itself before it ever shipped. {@link
 * #perProvider} is populated EAGERLY for all 11 endpoints in {@link #loadFromConfig()} (each
 * resolved through {@link ProviderConfigResolver}, which also applies the one-time legacy-
 * migration fallback for whichever endpoint {@code Config.ENDPOINT_CONFIG} currently names), so
 * {@link #saveToConfig()} can simply write every entry without tracking "was this one touched".
 */
public class PendingTranslatorConfig {

    /** Sentinel model selection meaning "use customModel instead of any preset". Not a real model
     *  id any provider would ever ship, so it can't collide with one. Also what Custom Provider
     *  (which has no presets at all) always uses -- its "preset list" is empty, so this sentinel is
     *  the only possible selection, and customModel is simply its free-typed model field. */
    public static final String CUSTOM_MODEL = "__custom__";

    /** Router mode -- the Simple screen's own Provider Mode CycleButton. */
    public ProviderMode providerMode;

    /** Which provider {@code ProviderMode.SINGLE} targets (mirrors {@code Config.ENDPOINT_CONFIG}).
     *  Ignored entirely by every other mode. Not editable through this round's GUI (see the
     *  completion report) -- ROUTER assigns SINGLE's target during migration; a future round can
     *  add a GUI control for changing it without needing any change here. */
    public Config.EndPoint endpoint;

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

    /** Per-provider scratch state for every {@link Config.EndPoint}, read/written only through the
     *  explicit-endpoint accessor methods below -- see the class javadoc for why there is no
     *  "currently active" live view any more. */
    private final Map<Config.EndPoint, ProviderPendingState> perProvider = new EnumMap<>(Config.EndPoint.class);

    private static final class ProviderPendingState {
        String modelSelection = CUSTOM_MODEL;
        String customModel = "";
        String apiKey = "";
        String customProviderName = "";
        String customProviderBaseUrl = "";
        AuthMode customProviderAuthMode = AuthMode.BEARER;
        boolean customProviderSupportsVision = false;
        boolean enabled = false;
        int priority = 1;
        int maxRequestsPerMinute = 10;
    }

    // Snapshotted once at construction (see class javadoc) -- compared against the live pending
    // values in translationRelevantSettingsChanged() to decide whether Done should offer to clear
    // the cache (mailbox review #002 point 1). endpoint/model are deliberately NOT snapshotted any
    // more (mailbox review round 023, plan's Translator.java section): switching provider/model no
    // longer changes what a cached translation would look like (the cache key stays (language,
    // text), never gaining a provider dimension), so it no longer belongs in this comparison --
    // only prompt/promptScreenshot actually change translation output.
    private final String originalPrompt;
    private final String originalPromptScreenshot;

    private PendingTranslatorConfig(Config.EndPoint endpoint, ProviderMode providerMode,
                                     boolean followGameLanguage, String targetLanguage,
                                     boolean tooltip, boolean jade, boolean ftb, boolean advancements, boolean screenshot,
                                     boolean icon, boolean pretranslate, String prompt, String promptScreenshot,
                                     boolean animation, int timeout, int rpm) {
        this.endpoint = endpoint;
        this.providerMode = providerMode;
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
            state.enabled = Config.CUSTOM_PROVIDER_ENABLED.get();
            state.priority = Config.CUSTOM_PROVIDER_PRIORITY.get();
            state.maxRequestsPerMinute = Config.CUSTOM_PROVIDER_MAX_REQUESTS_PER_MINUTE.get();
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

        Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(endpoint);
        state.enabled = keys.enabled().get();
        state.priority = keys.priority().get();
        state.maxRequestsPerMinute = keys.maxRequestsPerMinute().get();
        return state;
    }

    public static PendingTranslatorConfig loadFromConfig() {
        return new PendingTranslatorConfig(
                Config.ENDPOINT_CONFIG.get(), Config.PROVIDER_MODE.get(),
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

    // === Per-provider accessors -- see the class javadoc for why every one of these takes an
    // explicit Config.EndPoint rather than meaning "the active one". ===

    public boolean isProviderEnabled(Config.EndPoint ep) {
        return perProvider.get(ep).enabled;
    }

    public void setProviderEnabled(Config.EndPoint ep, boolean enabled) {
        perProvider.get(ep).enabled = enabled;
    }

    /** 1 = highest. See {@link Config#PROVIDER_GOOGLE}'s sibling comment for the full semantics
     *  (hard order in PRIORITY, a small bias in AUTOMATIC, ignored entirely in SINGLE). */
    public int getProviderPriority(Config.EndPoint ep) {
        return perProvider.get(ep).priority;
    }

    public void setProviderPriority(Config.EndPoint ep, int priority) {
        perProvider.get(ep).priority = priority;
    }

    /** This provider's OWN requests-per-minute budget -- distinct from the top-level {@link
     *  #maxRequestsPerMinute} field, which is the GLOBAL safety cap shared across every provider
     *  (see {@code Config.MAX_REQUESTS_PER_MINUTE}'s own comment). */
    public int getProviderMaxRequestsPerMinute(Config.EndPoint ep) {
        return perProvider.get(ep).maxRequestsPerMinute;
    }

    public void setProviderMaxRequestsPerMinute(Config.EndPoint ep, int rpm) {
        perProvider.get(ep).maxRequestsPerMinute = rpm;
    }

    /** One of this provider's own {@code ModelPreset} ids, or {@link #CUSTOM_MODEL}. Always {@link
     *  #CUSTOM_MODEL} for {@link Config.EndPoint#CUSTOM} (it has no presets). */
    public String getProviderModelSelection(Config.EndPoint ep) {
        return perProvider.get(ep).modelSelection;
    }

    public void setProviderModelSelection(Config.EndPoint ep, String modelSelection) {
        perProvider.get(ep).modelSelection = modelSelection;
    }

    /** The free-typed model id in effect when {@link #getProviderModelSelection} is {@link
     *  #CUSTOM_MODEL} (or always, for Custom Provider). */
    public String getProviderCustomModel(Config.EndPoint ep) {
        return perProvider.get(ep).customModel;
    }

    public void setProviderCustomModel(Config.EndPoint ep, String customModel) {
        perProvider.get(ep).customModel = customModel;
    }

    /** The model id that would actually be sent to this provider right now. */
    public String resolvedModelFor(Config.EndPoint ep) {
        return resolvedModelOf(perProvider.get(ep));
    }

    public String getProviderApiKey(Config.EndPoint ep) {
        return perProvider.get(ep).apiKey;
    }

    /** Callers are expected to have already sanitized {@code apiKey} via {@link #sanitizeApiKey}
     *  themselves (matches every EditBox responder's existing convention in this package) -- this
     *  setter does not sanitize again. */
    public void setProviderApiKey(Config.EndPoint ep, String apiKey) {
        perProvider.get(ep).apiKey = apiKey;
    }

    /** Meaningful only for {@link Config.EndPoint#CUSTOM}; harmless no-op storage for every other
     *  endpoint (never read for them). */
    public String getCustomProviderName(Config.EndPoint ep) {
        return perProvider.get(ep).customProviderName;
    }

    public void setCustomProviderName(Config.EndPoint ep, String name) {
        perProvider.get(ep).customProviderName = name;
    }

    public String getCustomProviderBaseUrl(Config.EndPoint ep) {
        return perProvider.get(ep).customProviderBaseUrl;
    }

    public void setCustomProviderBaseUrl(Config.EndPoint ep, String baseUrl) {
        perProvider.get(ep).customProviderBaseUrl = baseUrl;
    }

    public AuthMode getCustomProviderAuthMode(Config.EndPoint ep) {
        return perProvider.get(ep).customProviderAuthMode;
    }

    public void setCustomProviderAuthMode(Config.EndPoint ep, AuthMode authMode) {
        perProvider.get(ep).customProviderAuthMode = authMode;
    }

    public boolean getCustomProviderSupportsVision(Config.EndPoint ep) {
        return perProvider.get(ep).customProviderSupportsVision;
    }

    public void setCustomProviderSupportsVision(Config.EndPoint ep, boolean supportsVision) {
        perProvider.get(ep).customProviderSupportsVision = supportsVision;
    }

    /**
     * True if prompt/prompt_screenshot differ from what was loaded when this screen was opened --
     * exactly the values that change what a cached translation would have looked like, so a change
     * here (and only here) is what should prompt "clear the cache?".
     * <p>
     * <b>Deliberately narrowed from the pre-Router version</b> (mailbox review round 023's plan,
     * Translator.java section -- this is a disclosed, intentional behavior change, not an
     * oversight): endpoint/model used to be included here too, back when exactly one provider was
     * ever active and switching it changed which provider WOULD have produced a given cache entry.
     * Now that {@link ProviderMode#AUTOMATIC}/{@code ROUND_ROBIN} can already legitimately route the
     * SAME cached text through different providers over time as a normal, expected part of runtime
     * operation, treating a manual provider/model switch in the GUI as cache-invalidating would be
     * inconsistent with that -- the cache format itself never gained a provider dimension (still
     * plain {@code (language, text)}), so a translation from ANY provider satisfies the cache for
     * that text, and switching providers must never trigger a redundant re-translation of already-
     * cached text either automatically at runtime or via this confirmation prompt.
     */
    public boolean translationRelevantSettingsChanged() {
        return !prompt.equals(originalPrompt) || !promptScreenshot.equals(originalPromptScreenshot);
    }

    /**
     * Strips whitespace/control characters (including newlines) from a pasted or typed API key.
     * Delegates to {@link ApiKeyUtil#sanitize} -- kept as a static method here too (rather than
     * requiring every call site to import a different class) since this is the one already wired
     * into every EditBox responder in {@code ProviderDetailScreen}.
     */
    public static String sanitizeApiKey(String rawApiKey) {
        return ApiKeyUtil.sanitize(rawApiKey);
    }

    private static String resolvedModelOf(ProviderPendingState state) {
        return state.modelSelection.equals(CUSTOM_MODEL) ? state.customModel : state.modelSelection;
    }

    public void saveToConfig() {
        Config.ENDPOINT_CONFIG.set(endpoint);
        Config.PROVIDER_MODE.set(providerMode);

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
                Config.CUSTOM_PROVIDER_ENABLED.set(state.enabled);
                Config.CUSTOM_PROVIDER_PRIORITY.set(state.priority);
                Config.CUSTOM_PROVIDER_MAX_REQUESTS_PER_MINUTE.set(state.maxRequestsPerMinute);
            } else {
                Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(ep);
                if (keys.apiKey() != null) keys.apiKey().set(ApiKeyUtil.sanitize(state.apiKey));
                keys.model().set(ModelIdUtil.sanitize(resolvedModelOf(state)));
                keys.enabled().set(state.enabled);
                keys.priority().set(state.priority);
                keys.maxRequestsPerMinute().set(state.maxRequestsPerMinute);
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
        // with it, never leave them looking like leftover cruft. Written from SINGLE's own target
        // endpoint's current values (not a "the active screen field" live view any more -- see the
        // class javadoc), matching the pre-Router meaning of "whichever provider is in use".
        Config.API_KEY.set(ApiKeyUtil.sanitize(getProviderApiKey(endpoint)));
        Config.MODEL_NAME.set(ModelIdUtil.sanitize(resolvedModelFor(endpoint)));

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
        // Keeps the migration marker in sync with whatever mode the player just deliberately chose
        // -- without this, switching to AUTOMATIC (or any non-SINGLE mode) and saving would leave
        // the marker frozen at whatever migration originally wrote, causing the NEXT launch to
        // misread that deliberate choice as a downgrade-wipe and force it back (mailbox review
        // round 031, point Y1 -- see MicrodaerysTranslatorClient.syncProviderMigrationMarker's own
        // javadoc for the full failure mode this closes, and its known remaining gap).
        MicrodaerysTranslatorClient.syncProviderMigrationMarker(providerMode, endpoint);
        // Every Done here represents "the player changed their config" -- the one event that can
        // actually change whether NO_ELIGIBLE_PROVIDER still holds (mailbox review round 029, point
        // W1).
        Translator.resetProviderEligibilityErrorFlag();
    }
}
