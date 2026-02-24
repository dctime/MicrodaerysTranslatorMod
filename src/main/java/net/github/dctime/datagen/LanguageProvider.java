package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

public class LanguageProvider extends net.minecraftforge.common.data.LanguageProvider {

    public LanguageProvider(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("key."+ MicrodaerysTranslatorClient.MODID+".delete_translation_cache", "(刪除快取) Delete Translation Cache");
        add("key."+ MicrodaerysTranslatorClient.MODID+".show_translation_in_gui", "(在GUI顯示螢幕截圖翻譯內容 (按住)) Show Translation in GUI (Hold)");
        add("key."+ MicrodaerysTranslatorClient.MODID+".delete_showing_translation", "(重新翻譯內容 (按住)) Delete Displayed Translation (Hold)");
        add("key.categories."+ MicrodaerysTranslatorClient.MODID+".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.API_KEY_PATH, "(API 金鑰) API Key");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.MODEL_NAME_PATH, "(模型名稱) Model Name");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.PROMPT_PATH, "(提示語) Prompt");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.PROMPT_SCREENSHOT_PATH, "(翻譯螢幕截圖的提示語) Prompt for Screenshot Translation");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_JADE_CONFIG_PATH, "(啟用 Jade 整合) Enable Jade Integration");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "(啟用 FTB Quests 整合) Enable FTB Quests Integration");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "(啟用物品提示匡翻譯) Enable Tooltip Translation");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "(啟用成就翻譯) Enable Advancements Translation");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_SCREENSHOT_CONFIG_PATH, "(啟用螢幕截圖翻譯) Enable Screenshot Translation");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_ICON_CONFIG_PATH, "(Jade跟Tooltip翻譯第一行多傳物品圖案) Enable translation with icon for Jade and Tooltip (first line only)");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "(翻譯中是否在遊戲畫面顯示動畫) Enable translating animation");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.TIMEOUT_DURATION_CONFIG_PATH, "(多久timeout(秒)) Timeout Duration");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.FEATURE_TOGGLE_PATH, "(功能) Feature Toggle");
        add(MicrodaerysTranslatorClient.MODID+".configuration."+ Config.ENDPOINT_CONFIG_PATH, "(提供者) Endpoint");
    }
}
