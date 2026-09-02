package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/**
 * Traditional Chinese (zh_tw) strings -- see {@link LanguageProvider}'s javadoc for why this is a
 * separate locale file instead of one bilingual string per key. Every key here must also exist in
 * {@link LanguageProvider} (en_us is Minecraft's own fallback locale for keys missing here, e.g.
 * for a player on a locale that's neither zh_tw nor en_us), but not every en_us key needs an entry
 * here -- Minecraft falls back to en_us automatically for anything unlisted.
 */
public class LanguageProviderZhTw extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderZhTw(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "zh_tw");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "刪除快取");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "在GUI顯示螢幕截圖翻譯內容 (按住)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "重新翻譯內容 (按住)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "API 金鑰");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "模型名稱");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "提示語");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "翻譯螢幕截圖的提示語");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "啟用 Jade 整合");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "啟用 FTB Quests 整合");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "啟用物品提示匡翻譯");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "啟用成就翻譯");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "啟用螢幕截圖翻譯");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Jade跟Tooltip翻譯第一行多傳物品圖案");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "翻譯中是否在遊戲畫面顯示動畫");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "多久timeout(秒)");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "功能");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "提供者");

        add(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported",
                "翻譯失敗！目前選擇的模型不支援圖片輸入 -- 請到設定畫面換一個模型試試。");

        // === Custom config GUI (TranslatorConfigScreen / TranslatorAdvancedConfigScreen) ===
        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "翻譯服務");
        add(p + "service", "服務");
        add(p + "provider.google", "Google AI Studio");
        add(p + "provider.nvidia", "NVIDIA NIM");
        add(p + "provider.groq", "Groq");
        add(p + "provider.openrouter", "OpenRouter");
        add(p + "provider.mistral", "Mistral AI");
        add(p + "provider.deepseek", "DeepSeek");
        add(p + "provider.cerebras", "Cerebras");
        add(p + "provider.anthropic", "Anthropic Claude");
        add(p + "provider.openai", "OpenAI");
        add(p + "provider.ollama", "Ollama（本機）");
        add(p + "provider.custom", "自訂供應商");

        add(p + "provider_mode", "Provider 模式");
        add(p + "provider_mode.single", "單一");
        add(p + "provider_mode.single.tooltip", "只使用一個 provider，失敗時不會自動切換 -- 與本模組原本的行為相同。");
        add(p + "provider_mode.priority", "優先順序");
        add(p + "provider_mode.priority.tooltip", "依照 Priority 順序嘗試已啟用的 provider，遇到暫時性失敗（RPM 超限、逾時、連線錯誤、伺服器錯誤）時自動改用下一個。");
        add(p + "provider_mode.round_robin", "輪流");
        add(p + "provider_mode.round_robin.tooltip", "在已啟用且可用的 provider 之間平均輪流使用。");
        add(p + "provider_mode.automatic", "自動");
        add(p + "provider_mode.automatic.tooltip", "依目前負載、近期失敗紀錄與延遲自動挑選最合適的 provider，Priority 只作為次要參考。遇到暫時性失敗一樣會自動切換。建議使用。");
        add(p + "manage_providers", "管理 Provider");
        add(p + "manage_providers.note", "在這裡開關每個 provider。按下 Configure 可設定 API 金鑰、模型、優先順序與每分鐘請求數。");
        add(p + "provider.configure", "設定");
        add(p + "provider.enabled", "啟用");
        add(p + "provider.priority", "優先順序");
        add(p + "provider.priority.tooltip", "數字越小越優先：Priority 模式會先嘗試它，Automatic 模式會把它當作次要參考。1 為最高優先，大多數玩家不需要更動這個值。");
        add(p + "provider.rpm", "每分鐘請求數（此 provider）");
        add(p + "provider.rpm.tooltip", "這是本模組建議的起始值，不是官方保證的額度 -- 請依照你自己帳號實際的速率限制調整。");
        add(p + "provider.status", "狀態");
        add(p + "provider.status.ready", "就緒");
        add(p + "provider.status.rate_limited", "已達速率限制（%s 秒）");
        add(p + "provider.status.cooldown", "冷卻中（%s 秒）");
        add(p + "provider.status.invalid_key", "API 金鑰無效");
        add(p + "provider.status.cannot_connect", "無法連線");
        add(p + "provider.status.disabled", "已停用");
        add(p + "provider.status.detail.untried", "（未試過）");
        add(p + "provider.status.detail", "（平均 %s 秒，上次嘗試於 %s 秒前）");

        add(p + "model", "模型");
        add(p + "model.custom", "自訂...");
        add(p + "model.custom_id", "自訂模型 ID");
        add(p + "api_key", "API 金鑰");
        add(p + "api_key.show", "顯示");
        add(p + "api_key.hide", "隱藏");
        add(p + "api_key.paste", "貼上");
        add(p + "api_key.ollama_note", "本地服務，不需要 API 金鑰");
        add(p + "test_connection", "測試連線");
        add(p + "test_connection.testing", "測試中...");
        add(p + "test_connection.connected", "已連線");
        add(p + "test_connection.invalid_key", "API 金鑰無效");
        add(p + "test_connection.rate_limited", "已達速率限制");
        add(p + "test_connection.cannot_connect", "無法連線");
        add(p + "test_connection.http_error", "HTTP 錯誤 %s");
        add(p + "test_connection.model_not_found", "清單中找不到模型「%s」，清單可能不完整");
        add(p + "test_connection.invalid_base_url", "Base URL 無效");
        add(p + "test_connection.note", "僅驗證連線與授權，不保證一定能成功翻譯");

        add(p + "custom_provider.name", "供應商名稱");
        add(p + "custom_provider.base_url", "Base URL");
        add(p + "custom_provider.authentication", "驗證方式");
        add(p + "custom_provider.authentication.bearer", "Bearer Token");
        add(p + "custom_provider.authentication.none", "無");
        add(p + "custom_provider.supports_images", "支援圖片輸入");
        add(p + "custom_provider.privacy_note", "請求與翻譯內容會直接送到上面設定的伺服器。");

        add(p + "section.language", "語言");
        add(p + "follow_game_language", "跟隨 Minecraft 語言");
        add(p + "follow_game_language.tooltip", "使用你目前 Minecraft 選項裡的語言。");
        add(p + "target_language", "目標語言");
        add(p + "target_language.custom", "自訂/未知：%s");

        add(p + "section.features", "翻譯功能");
        add(p + "feature.tooltip", "物品提示框");
        add(p + "feature.jade", "Jade 資訊框");
        add(p + "feature.ftbquests", "FTB 任務");
        add(p + "feature.advancements", "進度");
        add(p + "feature.screenshot", "螢幕翻譯");

        add(p + "advanced_settings", "進階設定");
        add(p + "model_cache_note", "更換提供者或模型後，舊的翻譯快取不會自動失效/清除。");
        add(p + "clear_cache_confirm.title", "是否清除翻譯快取？");
        add(p + "clear_cache_confirm.message", "翻譯相關設定已變更。是否清除現有 %s 筆快取翻譯？");

        // === Advanced screen ===
        add(p + "section.translation", "翻譯");
        add(p + "include_icon", "翻譯時包含物品圖示");
        add(p + "include_icon.tooltip", "將物品圖示傳送給支援視覺辨識的模型，取得更好的翻譯內容。");
        add(p + "pretranslate_containers", "預先翻譯容器內物品");
        add(p + "pretranslate_containers.tooltip", "在你把滑鼠移到物品上之前，自動翻譯畫面上可見的容器物品，可能會用掉更多 API 請求。");
        add(p + "custom_prompt", "自訂翻譯提示語");
        add(p + "custom_prompt.builtin", "使用內建提示語");
        add(p + "custom_prompt.custom", "使用自訂提示語");
        add(p + "edit_custom_prompt", "編輯自訂提示語");
        add(p + "edit_screenshot_prompt", "編輯螢幕翻譯提示語");
        add(p + "reset_to_builtin", "重設為內建");

        add(p + "section.interface", "介面");
        add(p + "translating_animation", "顯示翻譯中動畫");
        add(p + "translating_animation.tooltip", "在等待翻譯結果時，於畫面上顯示一個小動畫。");

        add(p + "section.network", "網路");
        add(p + "timeout", "請求逾時");
        add(p + "timeout.seconds", "%s 秒");
        add(p + "timeout.custom", "自訂：%s 秒");
        add(p + "rpm", "每分鐘請求數");
        add(p + "rpm.tooltip", "限制 60 秒內最多能送出幾次 AI 翻譯請求。如果你的供應商回傳 HTTP 429，請調低這個數字。");
        add(p + "rpm.custom", "自訂：%s");

        add(p + "section.cache", "快取");
        add(p + "clear_cache", "清除翻譯快取（%s 筆）");

        add(p + "prompt_edit.title", "編輯自訂提示語");
        add(p + "prompt_edit.screenshot_title", "編輯螢幕翻譯提示語");
        add(p + "prompt_edit.hint", "留空 = 使用內建提示語");
    }
}
