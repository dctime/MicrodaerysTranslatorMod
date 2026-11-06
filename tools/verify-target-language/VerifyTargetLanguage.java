// Regression check for net.github.dctime.libs.TargetLanguage (the #5 "configurable target
// language" feature). Calls the REAL production class directly (no copy) -- TargetLanguage has
// no Minecraft/NeoForge dependency, same as JsonUtil/RetryPolicy, so no game runtime is needed.
//
// Language codes here are Minecraft's own format (zh_tw/zh_cn/ja_jp/en_us), not an invented
// scheme -- see the #6 round: this had to change so the same code could be compared directly
// against LanguageManager.getSelected() and lang/*.json file names.
//
// Not covered here: Translator's CacheKey record (language + text composite cache key) isn't
// independently tested. That's deliberate, not an oversight: a Java record's equals/hashCode is
// a language guarantee, not custom logic of ours -- there's nothing to verify beyond what the
// JDK already guarantees, and CacheKey is private to Translator, which (like the rest of
// Translator) can't be loaded headless anyway (see tools/verify-concurrency's disclosed limit).
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-target-language tools/verify-target-language/VerifyTargetLanguage.java
//   java -cp "tools/verify-target-language:$MAIN_CLASSES" VerifyTargetLanguage

import net.github.dctime.libs.TargetLanguage;

public class VerifyTargetLanguage {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- displayName ---
        assertTrue("zh_tw displays as 繁體中文", TargetLanguage.displayName("zh_tw").equals("繁體中文"));
        assertTrue("zh_cn displays as 简体中文", TargetLanguage.displayName("zh_cn").equals("简体中文"));
        assertTrue("ja_jp displays as 日文", TargetLanguage.displayName("ja_jp").equals("日文"));
        assertTrue("en_us displays as English", TargetLanguage.displayName("en_us").equals("English"));
        assertTrue("language code lookup is case-insensitive (ZH_TW)",
                TargetLanguage.displayName("ZH_TW").equals("繁體中文"));
        assertTrue("language code lookup tolerates surrounding whitespace ( zh_tw )",
                TargetLanguage.displayName(" zh_tw ").equals("繁體中文"));
        assertTrue("unknown language code falls back to showing the raw code, not a crash",
                TargetLanguage.displayName("klingon").equals("klingon"));

        // --- isAlreadyInTargetLanguage: zh_tw / zh_cn (CJK unified ideographs) ---
        assertTrue("zh_tw: Chinese text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("zh_tw", "你好世界"));
        assertTrue("zh_tw: English text is NOT already-translated",
                !TargetLanguage.isAlreadyInTargetLanguage("zh_tw", "Hello world"));
        assertTrue("zh_cn: Chinese text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("zh_cn", "你好世界"));

        // --- ja_jp: hiragana/katakana detected; kanji-only text is a documented known gap ---
        assertTrue("ja_jp: hiragana text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("ja_jp", "こんにちは"));
        assertTrue("ja_jp: katakana text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("ja_jp", "コンピュータ"));
        assertTrue("ja_jp: English text is NOT already-translated",
                !TargetLanguage.isAlreadyInTargetLanguage("ja_jp", "Hello world"));
        assertTrue("ja_jp: pure-kanji text is NOT caught by this heuristic (documented limitation, not a bug)",
                !TargetLanguage.isAlreadyInTargetLanguage("ja_jp", "世界"));

        // --- en_us: ASCII-only text counts as already English ---
        assertTrue("en_us: ASCII text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("en_us", "Hello world 123!"));
        assertTrue("en_us: Chinese text is NOT already-translated",
                !TargetLanguage.isAlreadyInTargetLanguage("en_us", "你好世界"));

        // --- unknown language / null safety: never skip, never throw ---
        assertTrue("unknown language code never skips translation (fails safe, doesn't throw)",
                !TargetLanguage.isAlreadyInTargetLanguage("klingon", "anything at all"));
        assertTrue("null text never skips translation and doesn't throw NPE",
                !TargetLanguage.isAlreadyInTargetLanguage("zh_tw", null));

        // --- Translator.resolvePrompt() substitution strategy: player-edited PROMPT/
        // PROMPT_SCREENSHOT config strings must never be run through String.format()/.formatted()
        // semantics, only literal String.replace("%s", ...). A prompt containing a lone '%' (e.g.
        // "不要翻超過 90% 的內容", perfectly reasonable text for a player to type) would make
        // .formatted() throw IllegalFormatException on every tooltip render; .replace() cannot
        // throw for any input. This pins the substitution approach down so nobody "simplifies"
        // resolvePrompt() back to .formatted() later.
        String playerEditedTemplateWithLoneMathPercent = "不要翻超過 90% 的內容，只回%s";
        String substituted = playerEditedTemplateWithLoneMathPercent.replace("%s", TargetLanguage.displayName("zh_tw"));
        assertTrue("a lone '%' in a player-edited prompt does not throw",
                substituted.equals("不要翻超過 90% 的內容，只回繁體中文"));
        try {
            String ignored = playerEditedTemplateWithLoneMathPercent.formatted(TargetLanguage.displayName("zh_tw"));
            throw new AssertionError("FAILED: expected .formatted() to throw for a lone '%' -- if it stopped throwing, "
                    + "this assertion's premise (why replace() is required) is stale and needs re-checking, not deleting");
        } catch (java.util.UnknownFormatConversionException expected) {
            System.out.println("OK: confirmed .formatted() DOES throw here (why resolvePrompt uses .replace() instead)");
        }

        // #7: the default PROMPT was rewritten to be language-agnostic and now uses %s three
        // times (target language name repeated for the "prefer official translations" clause).
        // String.replace() replaces every occurrence, not just the first -- confirm that holds.
        String multiPlaceholder = "只回%s的翻譯。名詞使用%s Minecraft 社群慣用譯名；有官方%s翻譯的詞優先採用官方翻譯。";
        String multiSubstituted = multiPlaceholder.replace("%s", TargetLanguage.displayName("ja_jp"));
        assertTrue("every occurrence of %s in the template gets replaced, not just the first",
                !multiSubstituted.contains("%s") && multiSubstituted.equals(
                        "只回日文的翻譯。名詞使用日文 Minecraft 社群慣用譯名；有官方日文翻譯的詞優先採用官方翻譯。"));

        // --- KNOWN_CODES: the GUI's language dropdown iterates this list, so it must actually
        // list every language displayName()/isAlreadyInTargetLanguage() know about, in a stable
        // order (see net.github.dctime.libs.TargetLanguage's KNOWN LinkedHashMap comment: this is
        // meant to be structurally impossible to drift, not just conventionally kept in sync).
        // List.equals() is order-sensitive, so this pins down BOTH membership and exact order in
        // one assertion. The order itself is a contract now (it's what the GUI dropdown shows the
        // player), not just "these ten codes exist" -- LinkedHashMap only preserves insertion
        // order, it doesn't stop someone from reordering the put() calls later.
        assertTrue("KNOWN_CODES lists exactly the ten curated languages, in this exact display order",
                TargetLanguage.KNOWN_CODES.equals(java.util.List.of("zh_tw", "zh_cn", "ja_jp", "en_us", "es_es", "fr_fr", "ko_kr", "ru_ru", "de_de", "pt_br")));
        for (String code : TargetLanguage.KNOWN_CODES) {
            assertTrue("KNOWN_CODES entry '" + code + "' has a real display name (not just the raw code echoed back)",
                    !TargetLanguage.displayName(code).equals(code));
        }

        System.out.println("ALL CHECKS PASSED");
    }
}
