package net.github.dctime;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Example config class for the translation mod.
 * 使用 NeoForge 的設定 API，集中管理模組設定。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum EndPoint {
        GOOGLE_AI_STUDIO,
        OLLAMA,
        MISTRAL
    }

    public static final String ENDPOINT_CONFIG_PATH = "endpoint";
    public static final ModConfigSpec.EnumValue<EndPoint> ENDPOINT_CONFIG = BUILDER
            .comment("[選哪個Endpoint] (預設 Google AI studio) Which Endpoint")
            .defineEnum(ENDPOINT_CONFIG_PATH, EndPoint.MISTRAL);

    // === Basic keys for Google AI Studio ===
    public static final String API_KEY_PATH = "api_key";
    public static final ModConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("The API KEY from Google AI Studio/Mistral AI [Google AI Studio/Mistral 的 API KEY]\n" +
                    "1. DIRECT CONNECTION: Data is sent ONLY to Google AI Studio / Mistral AI.\n" +
                    "2. NO DATA COLLECTION: This mod DOES NOT collect, log, or forward your API Key or prompts to any other servers.\n" +
                    "3. LOCAL STORAGE: Your key is stored exclusively in this local config file.\n" +
                    "4. CONSENT: Entering a key confirms you understand this data flow and agree to Google's Terms.\n\n" +
                    "[ZH-TW]\n" +
                    "1. 直連通訊：資料僅會直接傳送至提供者伺服器，不經過任何轉接。\n" +
                    "2. 拒絕蒐集：本模組「絕不」蒐集、紀錄或轉傳您的 API Key 及輸入內容至開發者或其他第三方伺服器。\n" +
                    "3. 本地儲存：您的金鑰僅儲存於此電腦的設定檔內，請妥善保管。\n" +
                    "4. 同意聲明：填入金鑰即代表您知悉上述資料流向，並同意提供者的服務條款。\n")
            .define(API_KEY_PATH, "");

    public static final String MODEL_NAME_PATH = "model_name";
    public static final ModConfigSpec.ConfigValue<String> MODEL_NAME = BUILDER
            .comment("The model name to use for translation [使用的模型]\n" +
                    "(Google 有 gemma-3-4b-it, Mistral 有 mistral-small-latest, ollama 要看你載什麼模型)")
            .define(MODEL_NAME_PATH, "mistral-small-latest");

    public static final String FOLLOW_GAME_LANGUAGE_PATH = "follow_game_language";
    public static final ModConfigSpec.BooleanValue FOLLOW_GAME_LANGUAGE = BUILDER
            .comment("[翻譯目標語言是否跟隨遊戲本身選擇的語言] (預設 true)\n" +
                    "開啟時忽略 target_language，改用遊戲選項裡目前選擇的語言；關閉時才用下面 target_language 手動指定。")
            .define(FOLLOW_GAME_LANGUAGE_PATH, true);

    public static final String TARGET_LANGUAGE_PATH = "target_language";
    public static final ModConfigSpec.ConfigValue<String> TARGET_LANGUAGE = BUILDER
            .comment("[翻譯輸出的目標語言] (預設 zh_tw) Target language for translation output\n" +
                    "只有 follow_game_language 關閉時才會用到這個值。\n" +
                    "代碼格式跟遊戲語言選項一致(底線+地區碼): zh_tw(繁體中文), zh_cn(简体中文), ja_jp(日文), en_us(English)。\n" +
                    "其他代碼一樣可以填，只是「原文已經是目標語言就跳過翻譯」這個偵測跟官方翻譯表都不會生效，Prompt 的語言名稱也會直接顯示代碼本身。")
            .define(TARGET_LANGUAGE_PATH, "zh_tw");

    // === Prompt tailored for Minecraft/mod tone (ASCII-safe, no fancy quotes) ===
    public static final String PROMPT_PATH = "prompt";
    public static final ModConfigSpec.ConfigValue<String> PROMPT = BUILDER
            .comment("The prompt to use for translation [翻譯時使用的提示語]\n" +
                    "留空(預設) = 依目標語言自動選擇內建的原生語言 prompt(每個語言各自一份，用該語言本身撰寫，不是同一份中文範本換語言名稱)。\n" +
                    "填了任何內容 = 不論目標語言是什麼，一律使用這份自訂文字(%s 會換成目標語言名稱)，即向下相容舊版設定。\n" +
                    "注意：NeoForge 的設定檔只有在這個 key 不存在時才會寫入新預設值；如果你在這次更新前就啟動過遊戲，這裡通常已經被寫入舊版的預設文字。" +
                    "這個 mod 認得幾個已知的舊版預設值，會自動當成留空處理，不用手動清空；只有你真的自己改過、寫過自訂內容時，才需要手動把這個值清空才能改用新版內建 prompt。")
            .define(PROMPT_PATH, "");

    public static final String PROMPT_SCREENSHOT_PATH = "prompt_screenshot";
    public static final ModConfigSpec.ConfigValue<String> PROMPT_SCREENSHOT = BUILDER
            .comment("The prompt to use for translation screenshots [翻譯螢幕截圖時使用的提示語]\n" +
                    "留空(預設) = 依目標語言自動選擇內建的原生語言 prompt；填了內容 = 對所有語言套用這份自訂文字，同上一項 prompt 的規則。\n" +
                    "已知的舊版預設值一樣會被自動辨識、當成留空處理，不用手動清空。")
            .define(PROMPT_SCREENSHOT_PATH, "");
//
//    // (Optional) 更嚴格版本：要求只輸出純文字一行，避免代碼框/前後空白
//    public static final ModConfigSpec.ConfigValue<String> PROMPT_STRICT = BUILDER
//            .comment("Strict prompt: force plain text output without code fences or extra whitespace.")
//            .define("Prompt Strict",
//                    "只回繁體中文翻譯本體(純文字，不要代碼區塊、不要前後空白、不要額外說明)。\n" +
//                            "規則相同：保留所有佔位符與格式；不翻譯 ID/路徑/Key/Tag/指令；遵循 zh_tw 官方譯名與台灣社群慣用語；直譯、簡潔。\n" +
//                            "待翻譯：{TEXT}"
//            );




    public static final String TIMEOUT_DURATION_CONFIG_PATH = "timeout_duration";
    public static final ModConfigSpec.ConfigValue<Integer> TIMEOUT_DURATION_CONFIG = BUILDER
            .comment("[timeout時間] (預設 30) Timeout Duration in seconds")
            .define(TIMEOUT_DURATION_CONFIG_PATH, 30);

    public static final String FEATURE_TOGGLE_PATH = "feature_toggle";

    static {
        BUILDER.push(FEATURE_TOGGLE_PATH);
    }

    // === Feature toggles ===
    public static final String ENABLE_TOOLTIP_TRANSLATION_PATH = "enable_tooltip_translation";
    public static final ModConfigSpec.BooleanValue ENABLE_TOOLTIP_TRANSLATION = BUILDER
            .comment("[滑鼠指向物品時是否啟用翻譯] (預設 true) Whether to enable tooltip translation")
            .define(ENABLE_TOOLTIP_TRANSLATION_PATH, true);


    public static final String ENABLE_FTB_QUEST_TRANSLATION_PATH = "enable_ftbquests_translation";
    public static final ModConfigSpec.BooleanValue ENABLE_FTB_QUEST_TRANSLATION = BUILDER
            .comment("[是否啟用 FTB Quests 翻譯] (預設 true) Whether to enable FTB Quests translation")
            .define(ENABLE_FTB_QUEST_TRANSLATION_PATH, true);

    public static final String ENABLE_JADE_CONFIG_PATH = "enable_jade_translation";
    public static final ModConfigSpec.BooleanValue ENABLE_JADE_CONFIG = BUILDER
            .comment("[是否啟用 Jade 翻譯] (預設 true) Whether to enable Jade translation")
            .define(ENABLE_JADE_CONFIG_PATH, true);

    public static final String ENABLE_ADVANCEMENTS_CONFIG_PATH = "enable_advancements_translation";
    public static final ModConfigSpec.BooleanValue ENABLE_ADVANCEMENTS_CONFIG = BUILDER
            .comment("[是否啟用成就翻譯] (預設 true) Whether to enable Advancements translation")
            .define(ENABLE_ADVANCEMENTS_CONFIG_PATH, true);

    public static final String ENABLE_SCREENSHOT_CONFIG_PATH = "enable_screenshot_translation";
    public static final ModConfigSpec.BooleanValue ENABLE_SCREENSHOT_CONFIG = BUILDER
            .comment("[是否啟用螢幕截圖翻譯] (預設 true) Whether to enable Screenshot translation")
            .define(ENABLE_SCREENSHOT_CONFIG_PATH, true);

    public static final String ENABLE_ICON_CONFIG_PATH = "enable_icon_translation";
    public static final ModConfigSpec.BooleanValue ENABLE_ICON_CONFIG = BUILDER
            .comment("[是否Jade跟Tooltip第一行多傳物品圖案] (預設 true) Whether to enable translation with icon for Jade and Tooltip (only first line)")
            .define(ENABLE_ICON_CONFIG_PATH, true);

    public static final String ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH = "enable_translating_animation";
    public static final ModConfigSpec.BooleanValue ENABLE_TRANSLATING_ANIMATION_CONFIG = BUILDER
            .comment("[翻譯中是否在遊戲畫面顯示動畫] (預設 true) Whether to show animation on GUI when translating")
            .define(ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, true);


    static {
        BUILDER.pop();
    }


    // // Example of item list config (kept as reference):
    // public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
    //         .comment("A list of items to log on common setup.")
    //         .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
