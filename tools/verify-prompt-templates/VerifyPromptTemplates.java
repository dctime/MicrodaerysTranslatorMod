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

        System.out.println("ALL CHECKS PASSED");
    }
}
