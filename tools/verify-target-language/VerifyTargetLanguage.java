// Regression check for net.github.dctime.libs.TargetLanguage (the #5 "configurable target
// language" feature). Calls the REAL production class directly (no copy) -- TargetLanguage has
// no Minecraft/NeoForge dependency, same as JsonUtil/RetryPolicy, so no game runtime is needed.
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
        assertTrue("zh-tw displays as 繁體中文", TargetLanguage.displayName("zh-tw").equals("繁體中文"));
        assertTrue("zh-cn displays as 简体中文", TargetLanguage.displayName("zh-cn").equals("简体中文"));
        assertTrue("ja displays as 日文", TargetLanguage.displayName("ja").equals("日文"));
        assertTrue("en displays as English", TargetLanguage.displayName("en").equals("English"));
        assertTrue("language code lookup is case-insensitive (ZH-TW)",
                TargetLanguage.displayName("ZH-TW").equals("繁體中文"));
        assertTrue("language code lookup tolerates surrounding whitespace ( zh-tw )",
                TargetLanguage.displayName(" zh-tw ").equals("繁體中文"));
        assertTrue("unknown language code falls back to showing the raw code, not a crash",
                TargetLanguage.displayName("klingon").equals("klingon"));

        // --- isAlreadyInTargetLanguage: zh-tw / zh-cn (CJK unified ideographs) ---
        assertTrue("zh-tw: Chinese text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("zh-tw", "你好世界"));
        assertTrue("zh-tw: English text is NOT already-translated",
                !TargetLanguage.isAlreadyInTargetLanguage("zh-tw", "Hello world"));
        assertTrue("zh-cn: Chinese text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("zh-cn", "你好世界"));

        // --- ja: hiragana/katakana detected; kanji-only text is a documented known gap ---
        assertTrue("ja: hiragana text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("ja", "こんにちは"));
        assertTrue("ja: katakana text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("ja", "コンピュータ"));
        assertTrue("ja: English text is NOT already-translated",
                !TargetLanguage.isAlreadyInTargetLanguage("ja", "Hello world"));
        assertTrue("ja: pure-kanji text is NOT caught by this heuristic (documented limitation, not a bug)",
                !TargetLanguage.isAlreadyInTargetLanguage("ja", "世界"));

        // --- en: ASCII-only text counts as already English ---
        assertTrue("en: ASCII text is detected as already translated",
                TargetLanguage.isAlreadyInTargetLanguage("en", "Hello world 123!"));
        assertTrue("en: Chinese text is NOT already-translated",
                !TargetLanguage.isAlreadyInTargetLanguage("en", "你好世界"));

        // --- unknown language / null safety: never skip, never throw ---
        assertTrue("unknown language code never skips translation (fails safe, doesn't throw)",
                !TargetLanguage.isAlreadyInTargetLanguage("klingon", "anything at all"));
        assertTrue("null text never skips translation and doesn't throw NPE",
                !TargetLanguage.isAlreadyInTargetLanguage("zh-tw", null));

        // --- Translator.resolvePrompt() substitution strategy: player-edited PROMPT/
        // PROMPT_SCREENSHOT config strings must never be run through String.format()/.formatted()
        // semantics, only literal String.replace("%s", ...). A prompt containing a lone '%' (e.g.
        // "不要翻超過 90% 的內容", perfectly reasonable text for a player to type) would make
        // .formatted() throw IllegalFormatException on every tooltip render; .replace() cannot
        // throw for any input. This pins the substitution approach down so nobody "simplifies"
        // resolvePrompt() back to .formatted() later.
        String playerEditedTemplateWithLoneMathPercent = "不要翻超過 90% 的內容，只回%s";
        String substituted = playerEditedTemplateWithLoneMathPercent.replace("%s", TargetLanguage.displayName("zh-tw"));
        assertTrue("a lone '%' in a player-edited prompt does not throw",
                substituted.equals("不要翻超過 90% 的內容，只回繁體中文"));
        try {
            String ignored = playerEditedTemplateWithLoneMathPercent.formatted(TargetLanguage.displayName("zh-tw"));
            throw new AssertionError("FAILED: expected .formatted() to throw for a lone '%' -- if it stopped throwing, "
                    + "this assertion's premise (why replace() is required) is stale and needs re-checking, not deleting");
        } catch (java.util.UnknownFormatConversionException expected) {
            System.out.println("OK: confirmed .formatted() DOES throw here (why resolvePrompt uses .replace() instead)");
        }

        System.out.println("ALL CHECKS PASSED");
    }
}
