package net.github.dctime.screen;

import net.github.dctime.Config;

import java.util.EnumMap;
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
 */
public class PendingTranslatorConfig {

    /** Sentinel model selection meaning "use customModel instead of any preset". Not a real model
     *  id any provider would ever ship, so it can't collide with one. */
    public static final String CUSTOM_MODEL = "__custom__";

    public Config.EndPoint endpoint;
    /** One of the current provider's ModelPreset ids, or {@link #CUSTOM_MODEL}. */
    public String modelSelection;
    /** The actual model id in effect when modelSelection == CUSTOM_MODEL. Kept up to date by
     *  {@link #onEndpointChanged} even while a preset is selected, so switching back to Custom
     *  never loses what was last typed. */
    public String customModel;
    /** The model the player last had selected for each endpoint, so switching Service and back
     *  doesn't strand them on a preset id from a DIFFERENT provider. Populated as endpoints are
     *  left, in {@link #onEndpointChanged}, not pre-seeded -- see that method. */
    private final Map<Config.EndPoint, String> lastModelPerEndpoint = new EnumMap<>(Config.EndPoint.class);
    public String apiKey;
    public boolean apiKeyRevealed;
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

    // Snapshotted once at construction (see class javadoc) -- compared against the live pending
    // values in translationRelevantSettingsChanged() to decide whether Done should offer to clear
    // the cache (mailbox review #002 point 1).
    private final Config.EndPoint originalEndpoint;
    private final String originalModel;
    private final String originalPrompt;
    private final String originalPromptScreenshot;

    private PendingTranslatorConfig(Config.EndPoint endpoint, String modelName, String apiKey,
                                     boolean followGameLanguage, String targetLanguage,
                                     boolean tooltip, boolean jade, boolean ftb, boolean advancements, boolean screenshot,
                                     boolean icon, boolean pretranslate, String prompt, String promptScreenshot,
                                     boolean animation, int timeout, int rpm) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
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

        this.originalEndpoint = endpoint;
        this.originalModel = modelName;
        this.originalPrompt = prompt;
        this.originalPromptScreenshot = promptScreenshot;

        // Never overwrite an unrecognized model_name with a preset (backward compatibility with
        // whatever the player already had in the TOML): only pick a preset selection if the saved
        // model_name is byte-identical to one of THIS provider's presets; anything else -- a typo,
        // a model this mod doesn't list, or a preset id that belongs to a *different* provider --
        // is Custom, with the real string preserved in customModel.
        this.customModel = modelName;
        this.modelSelection = ProviderInfo.of(endpoint).models().stream()
                .anyMatch(p -> p.modelId().equals(modelName)) ? modelName : CUSTOM_MODEL;
    }

    public static PendingTranslatorConfig loadFromConfig() {
        return new PendingTranslatorConfig(
                Config.ENDPOINT_CONFIG.get(), Config.MODEL_NAME.get(), Config.API_KEY.get(),
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

    /**
     * Call when the player switches Service in the GUI.
     * <p>
     * Remembers what was selected for the endpoint being LEFT (so switching away and back
     * round-trips correctly, including a Custom value), and for the NEW endpoint either restores
     * what was last selected there, or -- if this is the first visit to it this session -- falls
     * back to that provider's own recommended preset (index 0 of {@link ProviderInfo#models()}).
     * <p>
     * This is a deliberate fix for a real trap the naive "just re-resolve against the new
     * provider's presets" version had: switching from Mistral (model_name=mistral-small-latest)
     * to Google AI Studio would leave Model showing "Custom... / mistral-small-latest" -- a
     * brand-new player's very first provider switch would hand them a guaranteed-404 config, with
     * no indication anything was wrong until Test Connection (or a real translation) failed. See
     * mailbox review #002 point E3.
     */
    public void onEndpointChanged(Config.EndPoint newEndpoint) {
        lastModelPerEndpoint.put(this.endpoint, resolvedModel());
        this.endpoint = newEndpoint;

        String remembered = lastModelPerEndpoint.get(newEndpoint);
        String target = remembered != null ? remembered : ProviderInfo.of(newEndpoint).models().get(0).modelId();
        this.customModel = target;
        this.modelSelection = ProviderInfo.of(newEndpoint).models().stream()
                .anyMatch(p -> p.modelId().equals(target)) ? target : CUSTOM_MODEL;
    }

    /**
     * True if endpoint/model/prompt/prompt_screenshot differ from what was loaded when this
     * screen was opened -- exactly the values that change what a cached translation would have
     * looked like, so a change here (and only here) is what should prompt "clear the cache?".
     */
    public boolean translationRelevantSettingsChanged() {
        return endpoint != originalEndpoint
                || !resolvedModel().equals(originalModel)
                || !prompt.equals(originalPrompt)
                || !promptScreenshot.equals(originalPromptScreenshot);
    }

    /**
     * Strips whitespace/control characters (including newlines) from a pasted or typed API key.
     * <p>
     * {@code Minecraft.getInstance().keyboardHandler.getClipboard()} does NOT strip these (it only
     * filters broken surrogate pairs) -- and a trailing newline is one of the most common ways an
     * API key ends up on the clipboard (copied from a webpage or a text file). An unsanitized key
     * containing a newline makes {@code HttpRequest.Builder.header("x-goog-api-key", apiKey)}
     * throw {@code IllegalArgumentException} -- SYNCHRONOUSLY, before any network call, so this
     * isn't just a failed request: Test Connection's click handler crashes on the render thread,
     * and once such a key is saved, so does every real translation's tooltip render (see mailbox
     * review #002 point E1). Applied here as the one choke point every path (typing, this mod's
     * own Paste button, and EditBox's own native Ctrl+V, which bypasses that Paste button
     * entirely) ends up going through before the key is actually used or saved. Safe for any real
     * API key: they don't contain whitespace or control characters to begin with.
     */
    public static String sanitizeApiKey(String rawApiKey) {
        return rawApiKey == null ? "" : rawApiKey.replaceAll("[\\p{Cntrl}\\s]", "");
    }

    public void saveToConfig() {
        Config.ENDPOINT_CONFIG.set(endpoint);
        Config.MODEL_NAME.set(resolvedModel());
        Config.API_KEY.set(sanitizeApiKey(apiKey));
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
