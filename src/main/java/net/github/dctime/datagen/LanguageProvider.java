package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/**
 * English (en_us) strings. Also the fallback locale Minecraft uses for any key missing from the
 * player's actual language file (e.g. {@link LanguageProviderZhTw}) -- so every key that exists
 * anywhere must exist here.
 * <p>
 * Each locale gets its OWN provider/file with ONLY that language's text -- not one string with
 * both languages crammed together ("(中文) English"), which was this project's older convention
 * for a handful of short config-comment labels. That convention breaks down badly for full GUI
 * text: every label becomes roughly twice as long as it needs to be, which is exactly what
 * doesn't fit in a fixed-width button, and a Chinese-speaking player still has to visually parse
 * past an English half (and vice versa) they never asked to see. Minecraft's own translation
 * system already resolves {@code Component.translatable(key)} against whatever locale the player
 * has selected -- that's what per-locale files are for.
 */
public class LanguageProvider extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProvider(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "Delete Translation Cache");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "Show Translation in GUI (Hold)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "Delete Displayed Translation (Hold)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "API Key");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "Model Name");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "Prompt");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "Prompt for Screenshot Translation");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Enable Jade Integration");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "Enable FTB Quests Integration");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "Enable Tooltip Translation");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "Enable Advancements Translation");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "Enable Screenshot Translation");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Enable translation with icon for Jade and Tooltip (first line only)");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "Enable translating animation");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "Timeout Duration");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "Feature Toggle");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "Endpoint");

        // === Custom config GUI (TranslatorConfigScreen / TranslatorAdvancedConfigScreen) ===
        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "Translation Service");
        add(p + "service", "Service");
        add(p + "model", "Model");
        add(p + "model.custom", "Custom...");
        add(p + "model.custom_id", "Custom Model ID");
        add(p + "api_key", "API Key");
        add(p + "api_key.show", "Show");
        add(p + "api_key.hide", "Hide");
        add(p + "api_key.paste", "Paste");
        add(p + "api_key.ollama_note", "Local service - API key not required");
        add(p + "test_connection", "Test Connection");
        add(p + "test_connection.testing", "Testing...");
        add(p + "test_connection.connected", "Connected");
        add(p + "test_connection.invalid_key", "Invalid API Key");
        add(p + "test_connection.rate_limited", "Rate limit exceeded");
        add(p + "test_connection.cannot_connect", "Cannot connect");
        add(p + "test_connection.http_error", "HTTP Error %s");
        add(p + "test_connection.model_not_found", "Model '%s' not found in list (list may be incomplete)");
        add(p + "test_connection.note", "Confirms connection & authorization only -- not a guarantee that translation requests will succeed");

        add(p + "section.language", "Language");
        add(p + "follow_game_language", "Follow Minecraft Language");
        add(p + "follow_game_language.tooltip", "Uses your current Minecraft language.");
        add(p + "target_language", "Target Language");
        add(p + "target_language.custom", "Custom/Unknown: %s");

        add(p + "section.features", "Translation Features");
        add(p + "feature.tooltip", "Item Tooltip");
        add(p + "feature.jade", "Jade Tooltip");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "Advancements");
        add(p + "feature.screenshot", "Screenshot Translation");

        add(p + "advanced_settings", "Advanced Settings");
        add(p + "model_cache_note", "Changing provider or model does not automatically invalidate or clear old cached translations.");
        add(p + "clear_cache_confirm.title", "Clear Translation Cache?");
        add(p + "clear_cache_confirm.message", "Translation-related settings changed. Clear the existing %s cached translations?");

        // === Advanced screen ===
        add(p + "section.translation", "Translation");
        add(p + "include_icon", "Include Item Icon in Translation");
        add(p + "include_icon.tooltip", "Sends the item icon to supported vision models for better translation context.");
        add(p + "pretranslate_containers", "Pretranslate Container Items");
        add(p + "pretranslate_containers.tooltip", "Automatically translates visible container items before you hover over them. This may use more API requests.");
        add(p + "custom_prompt", "Custom Translation Prompt");
        add(p + "custom_prompt.builtin", "Use Built-in Prompt");
        add(p + "custom_prompt.custom", "Custom Prompt");
        add(p + "edit_custom_prompt", "Edit Custom Prompt");
        add(p + "edit_screenshot_prompt", "Edit Screenshot Prompt");
        add(p + "reset_to_builtin", "Reset to Built-in");

        add(p + "section.interface", "Interface");
        add(p + "translating_animation", "Show Translating Animation");
        add(p + "translating_animation.tooltip", "Shows a small animation on screen while waiting for a translation result.");

        add(p + "section.network", "Network");
        add(p + "timeout", "Request Timeout");
        add(p + "timeout.seconds", "%s sec");
        add(p + "timeout.custom", "Custom: %s sec");
        add(p + "rpm", "Requests per Minute");
        add(p + "rpm.tooltip", "Limits how many AI translation requests can be sent within 60 seconds. Lower this value if your provider returns HTTP 429.");
        add(p + "rpm.custom", "Custom: %s");

        add(p + "section.cache", "Cache");
        add(p + "clear_cache", "Clear Translation Cache (%s entries)");

        add(p + "prompt_edit.title", "Edit Custom Prompt");
        add(p + "prompt_edit.screenshot_title", "Edit Screenshot Prompt");
        add(p + "prompt_edit.hint", "Leave blank to use the built-in prompt");
    }
}
