// Regression check for net.github.dctime.libs.PromptTemplates (#8: per-language native prompts)
// and the TargetLanguage additions (es_es/fr_fr) that go with it. Calls the REAL production
// classes directly (no copy) -- both have no Minecraft/NeoForge dependency.
//
// What this DOES verify: every known language's templates are non-blank, self-contained (no
// leftover "%s" placeholder -- they're written natively, not templated), and each one is
// actually written in the right language (spot-checked via a distinctive native word/phrase).
// The unknown-language fallback correctly substitutes the raw code via %s.
//
// What this does NOT verify: translation quality / grammatical correctness of the ja_jp/es_es/
// fr_fr wording -- no native speaker or live model reviewed those. If this mod ever gets a
// native speaker or a real Gemini call to sanity-check a template, that check belongs here.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-prompt-templates tools/verify-prompt-templates/VerifyPromptTemplates.java
//   java -cp "tools/verify-prompt-templates:$MAIN_CLASSES" VerifyPromptTemplates

import net.github.dctime.libs.PromptTemplates;
import net.github.dctime.libs.TargetLanguage;

public class VerifyPromptTemplates {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        String[] known = {"zh_tw", "zh_cn", "ja_jp", "en_us", "es_es", "fr_fr"};
        for (String lang : known) {
            String prompt = PromptTemplates.promptFor(lang);
            String screenshot = PromptTemplates.screenshotPromptFor(lang);
            assertTrue(lang + ": prompt is non-blank", prompt != null && !prompt.isBlank());
            assertTrue(lang + ": screenshot prompt is non-blank", screenshot != null && !screenshot.isBlank());
            assertTrue(lang + ": prompt has no leftover %s placeholder (written natively, not templated)",
                    !prompt.contains("%s"));
            assertTrue(lang + ": screenshot prompt has no leftover %s placeholder",
                    !screenshot.contains("%s"));
        }

        // spot-check each is actually written in the right language, not e.g. all English
        assertTrue("zh_tw prompt is written in Chinese (contains 繁體中文)", PromptTemplates.promptFor("zh_tw").contains("繁體中文"));
        assertTrue("zh_cn prompt is written in simplified Chinese (contains 简体中文)", PromptTemplates.promptFor("zh_cn").contains("简体中文"));
        assertTrue("ja_jp prompt is written in Japanese (contains 日本語)", PromptTemplates.promptFor("ja_jp").contains("日本語"));
        assertTrue("en_us prompt is written in English (contains \"English translation\")", PromptTemplates.promptFor("en_us").contains("English translation"));
        assertTrue("es_es prompt is written in Spanish (contains \"traducción al español\")", PromptTemplates.promptFor("es_es").contains("traducción al español"));
        assertTrue("fr_fr prompt is written in French (contains \"traduction en français\")", PromptTemplates.promptFor("fr_fr").contains("traduction en français"));

        // unknown language code: generic English-authored fallback, %s substituted with the raw code
        String unknownPrompt = PromptTemplates.promptFor("klingon");
        assertTrue("unknown language falls back to the generic template (English-authored)",
                unknownPrompt.contains("Reply with ONLY the klingon translation"));
        assertTrue("unknown language fallback has no leftover %s either",
                !unknownPrompt.contains("%s"));

        // TargetLanguage additions that ship alongside the new templates
        assertTrue("es_es displays as Español", TargetLanguage.displayName("es_es").equals("Español"));
        assertTrue("fr_fr displays as Français", TargetLanguage.displayName("fr_fr").equals("Français"));
        assertTrue("es_es never reports \"already translated\" for plain English text (avoids misfiring on the ASCII overlap with English)",
                !TargetLanguage.isAlreadyInTargetLanguage("es_es", "Iron Ingot"));
        assertTrue("es_es never reports \"already translated\" even for genuinely accented Spanish text",
                !TargetLanguage.isAlreadyInTargetLanguage("es_es", "Lingote de Hierro"));
        assertTrue("fr_fr never reports \"already translated\" for plain English text",
                !TargetLanguage.isAlreadyInTargetLanguage("fr_fr", "Iron Ingot"));

        // --- legacy-default recognition: confirmed against a real player's config in the wild ---
        // this is the EXACT prompt value a real player's config.toml still had, generated before
        // this mod had per-language templates at all (no %s anywhere in it).
        String realPlayerLegacyPrompt =
                "只回繁體中文的翻譯，不要多字、不要解釋。\n" +
                "遵守：\n" +
                "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
                "名詞遵循遊戲慣用：block=方塊、slab=半磚、stairs=樓梯、planks=木材、log=原木、ore=礦石、ingot=錠、nugget=金粒、dye=染料、bucket=桶、stack=堆疊、craft=合成、smelt=熔煉、furnace=熔爐、blast furnace=高爐、smoker=煙燻爐、enchant=附魔、anvil=鐵砧、loot=戰利品、biome=生態域\n" +
                "優先使用《Minecraft》繁中(zh_tw)官方譯名；無官方譯名則用台灣社群慣用語。\n" +
                "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
                "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
                "待翻譯：\n";
        assertTrue("the exact legacy prompt pulled from a real player's config.toml is recognized as legacy",
                PromptTemplates.isBlankOrLegacyDefault(realPlayerLegacyPrompt));

        // the two other historical defaults this mod shipped (#5's half-parameterized version,
        // #7's short-lived language-agnostic version) must also be recognized
        String v5Default = "只回%s的翻譯，不要多字、不要解釋。\n" +
                "遵守：\n" +
                "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
                "名詞遵循遊戲慣用：block=方塊、slab=半磚、stairs=樓梯、planks=木材、log=原木、ore=礦石、ingot=錠、nugget=金粒、dye=染料、bucket=桶、stack=堆疊、craft=合成、smelt=熔煉、furnace=熔爐、blast furnace=高爐、smoker=煙燻爐、enchant=附魔、anvil=鐵砧、loot=戰利品、biome=生態域\n" +
                "優先使用《Minecraft》繁中(zh_tw)官方譯名；無官方譯名則用台灣社群慣用語。\n" +
                "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
                "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
                "待翻譯：\n";
        assertTrue("the #5 half-parameterized historical default is recognized as legacy",
                PromptTemplates.isBlankOrLegacyDefault(v5Default));

        String v7Default = "只回%s的翻譯，不要多字、不要解釋。\n" +
                "遵守：\n" +
                "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
                "名詞使用%s Minecraft 社群慣用譯名；有官方%s翻譯的詞優先採用官方翻譯。\n" +
                "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
                "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
                "待翻譯：\n";
        assertTrue("the #7 language-agnostic historical default is recognized as legacy",
                PromptTemplates.isBlankOrLegacyDefault(v7Default));

        assertTrue("blank is still recognized (legacy-detection is additive, doesn't replace it)",
                PromptTemplates.isBlankOrLegacyDefault(""));
        assertTrue("a genuine player-authored custom prompt is NOT mistaken for a legacy default",
                !PromptTemplates.isBlankOrLegacyDefault("請用海盜的語氣翻譯成英文"));

        // same three generations for the screenshot prompt's own separate history
        String screenshotV1 = """
                請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成繁體中文

                翻譯的格式為
                畫面簡介:xxx\\n
                xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)
                xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)
                """;
        String screenshotV5 = """
                請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成%s

                翻譯的格式為
                畫面簡介:xxx\\n
                xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)
                xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)
                """;
        String screenshotV7 = """
                請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成%s

                翻譯的格式為
                畫面簡介:xxx\\n
                xxx/xxx\\n(原文英文1/%s譯文1)(括號裡不需要顯示)
                xxx/xxx\\n(原文英文2/%s譯文2)(括號裡不需要顯示)
                """;
        assertTrue("screenshot prompt v1 (pre-target_language) is recognized as legacy",
                PromptTemplates.isBlankOrLegacyScreenshotDefault(screenshotV1));
        assertTrue("screenshot prompt v5 (#5 half-parameterized) is recognized as legacy",
                PromptTemplates.isBlankOrLegacyScreenshotDefault(screenshotV5));
        assertTrue("screenshot prompt v7 (#7 language-agnostic) is recognized as legacy",
                PromptTemplates.isBlankOrLegacyScreenshotDefault(screenshotV7));
        assertTrue("blank screenshot prompt is still recognized",
                PromptTemplates.isBlankOrLegacyScreenshotDefault(""));

        System.out.println("ALL CHECKS PASSED");
    }
}
