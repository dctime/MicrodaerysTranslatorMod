package net.github.dctime;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Example config class for the translation mod.
 * 使用 NeoForge 的設定 API，集中管理模組設定。
 */

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // === Basic keys for Google AI Studio ===
    public static final String API_KEY_PATH = "api_key";
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("The API KEY from Google AI Studio [Google AI Studio 的 API KEY]\n" +
                    "1. DIRECT CONNECTION: Data is sent ONLY to Google AI Studio (https://generativelanguage.googleapis.com).\n" +
                    "2. NO DATA COLLECTION: This mod DOES NOT collect, log, or forward your API Key or prompts to any other servers.\n" +
                    "3. LOCAL STORAGE: Your key is stored exclusively in this local config file.\n" +
                    "4. CONSENT: Entering a key confirms you understand this data flow and agree to Google's Terms.\n\n" +
                    "[ZH-TW]\n" +
                    "1. 直連通訊：資料僅會直接傳送至 Google AI Studio 官方伺服器，不經過任何轉接。\n" +
                    "2. 拒絕蒐集：本模組「絕不」蒐集、紀錄或轉傳您的 API Key 及輸入內容至開發者或其他第三方伺服器。\n" +
                    "3. 本地儲存：您的金鑰僅儲存於此電腦的設定檔內，請妥善保管。\n" +
                    "4. 同意聲明：填入金鑰即代表您知悉上述資料流向，並同意 Google 的服務條款。\n" +
                    "(Apply one for free at https://ai.google.dev/)")
            .define(API_KEY_PATH, "");

    public static final String MODEL_NAME_PATH = "model_name";
    public static final ForgeConfigSpec.ConfigValue<String> MODEL_NAME = BUILDER
            .comment("The model name to use for translation [使用的 Google AI Studio 模型]\n" +
                    "(免費可用 Gemma，如：gemma-3-4b-it 不考慮翻譯速度的話用 gemma-3-27b-it)")
            .define(MODEL_NAME_PATH, "gemma-3-4b-it");

    // === Prompt tailored for Minecraft/mod tone (ASCII-safe, no fancy quotes) ===
    public static final String PROMPT_PATH = "prompt";
    public static final ForgeConfigSpec.ConfigValue<String> PROMPT = BUILDER
            .comment("The prompt to use for translation [翻譯時使用的提示語]\n" +
                    "貼近 Minecraft/模組語氣。保留佔位符與格式。不腦補。僅輸出翻譯文字。")
            .define(PROMPT_PATH,
                    "只回繁體中文的翻譯，不要多字、不要解釋。\n" +
                    "遵守：\n" +
                    "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
                    "名詞遵循遊戲慣用：block=方塊、slab=半磚、stairs=樓梯、planks=木材、log=原木、ore=礦石、ingot=錠、nugget=金粒、dye=染料、bucket=桶、stack=堆疊、craft=合成、smelt=熔煉、furnace=熔爐、blast furnace=高爐、smoker=煙燻爐、enchant=附魔、anvil=鐵砧、loot=戰利品、biome=生態域\n" +
                    "優先使用《Minecraft》繁中(zh_tw)官方譯名；無官方譯名則用台灣社群慣用語。\n" +
                    "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
                    "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
                    "待翻譯：\n"
            );

    public static final String PROMPT_SCREENSHOT_PATH = "prompt_screenshot";
    public static final ForgeConfigSpec.ConfigValue<String> PROMPT_SCREENSHOT = BUILDER
            .comment("The prompt to use for translation screenshots [翻譯螢幕截圖時使用的提示語]\n")
            .define(PROMPT_SCREENSHOT_PATH,
                    """
                    請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成繁體中文
                    
                    翻譯的格式為
                    畫面簡介:xxx\\n
                    xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)
                    xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)
                    """
            );
//
//    // (Optional) 更嚴格版本：要求只輸出純文字一行，避免代碼框/前後空白
//    public static final ModConfigSpec.ConfigValue<String> PROMPT_STRICT = BUILDER
//            .comment("Strict prompt: force plain text output without code fences or extra whitespace.")
//            .define("Prompt Strict",
//                    "只回繁體中文翻譯本體(純文字，不要代碼區塊、不要前後空白、不要額外說明)。\n" +
//                            "規則相同：保留所有佔位符與格式；不翻譯 ID/路徑/Key/Tag/指令；遵循 zh_tw 官方譯名與台灣社群慣用語；直譯、簡潔。\n" +
//                            "待翻譯：{TEXT}"
//            );

    // === Feature toggles ===
    public static final String ENABLE_TOOLTIP_TRANSLATION_PATH = "enable_tooltip_translation";
    public static final ForgeConfigSpec.BooleanValue ENABLE_TOOLTIP_TRANSLATION = BUILDER
            .comment("[滑鼠指向物品時是否啟用翻譯] (預設 true) Whether to enable tooltip translation")
            .define(ENABLE_TOOLTIP_TRANSLATION_PATH, true);


    public static final String ENABLE_FTB_QUEST_TRANSLATION_PATH = "enable_ftbquests_translation";
    public static final ForgeConfigSpec.BooleanValue ENABLE_FTB_QUEST_TRANSLATION = BUILDER
            .comment("[是否啟用 FTB Quests 翻譯] (預設 true) Whether to enable FTB Quests translation")
            .define(ENABLE_FTB_QUEST_TRANSLATION_PATH, true);

    public static final String ENABLE_JADE_CONFIG_PATH = "enable_jade_translation";
    public static final ForgeConfigSpec.BooleanValue ENABLE_JADE_CONFIG = BUILDER
            .comment("[是否啟用 Jade 翻譯] (預設 true) Whether to enable Jade translation")
            .define(ENABLE_JADE_CONFIG_PATH, true);

    public static final String ENABLE_ADVANCEMENTS_CONFIG_PATH = "enable_advancements_translation";
    public static final ForgeConfigSpec.BooleanValue ENABLE_ADVANCEMENTS_CONFIG = BUILDER
            .comment("[是否啟用成就翻譯] (預設 true) Whether to enable Advancements translation")
            .define(ENABLE_ADVANCEMENTS_CONFIG_PATH, true);

    public static final String ENABLE_SCREENSHOT_CONFIG_PATH = "enable_screenshot_translation";
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCREENSHOT_CONFIG = BUILDER
            .comment("[是否啟用螢幕截圖翻譯] (預設 true) Whether to enable Screenshot translation")
            .define(ENABLE_SCREENSHOT_CONFIG_PATH, true);

    public static final String ENABLE_ICON_CONFIG_PATH = "enable_icon_translation";
    public static final ForgeConfigSpec.BooleanValue ENABLE_ICON_CONFIG = BUILDER
            .comment("[是否Jade跟Tooltip第一行多傳物品圖案] (預設 true) Whether to enable translation with icon for Jade and Tooltip (only first line)")
            .define(ENABLE_ICON_CONFIG_PATH, true);

    // // Example of item list config (kept as reference):
    // public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
    //         .comment("A list of items to log on common setup.")
    //         .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ForgeConfigSpec SPEC = BUILDER.build();

//    private static boolean validateItemName(final Object obj) {
//        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
//    }
}
