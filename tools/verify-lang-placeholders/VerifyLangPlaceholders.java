// Regression check for the generated lang files (src/generated/resources/assets/
// microdaerystranslator/lang/*.json), guarding against the exact crash mailbox review #002 found
// by hand: a value with TWO bare "%s" placeholders but only one argument ever passed to
// Component.translatable(key, oneArg) throws TranslatableFormatException the first time that
// line actually renders -- not at compile time, not in any of the other verify-* checks.
//
// The actual invariant checked here (mailbox review #002 point H3, tightened from an earlier,
// looser version of this file): for every key, the number of bare "%s" placeholders must be THE
// SAME across every one of the 10 language files. That -- not "never more than one" -- is the
// real thing that keeps this mod from crashing: Component.translatable(key, args...) always
// passes the SAME Java-side args regardless of which language file ends up resolving the key, so
// if de_de has one %s for a key but en_us has two, whichever one runs with too few args throws.
// A key legitimately needing two arguments is completely valid AS LONG AS every language's value
// for that key also has exactly two -- this file does not forbid that, only inconsistency.
//
// The unavoidable coupling this does NOT (and cannot, from data alone) verify: the placeholder
// count agreed on across all 10 files must ALSO match how many arguments the Java call site
// actually passes to Component.translatable(key, ...). If every language file consistently has
// two %s for a key but the Java code only ever passes one argument, this check passes cleanly and
// the game still crashes the first time that key renders. That half of the invariant lives in the
// Java source, not the data, and isn't exercised by this file.
//
// A literal substring count of "%s" (not a regex) is deliberate and sufficient: "%1$s" does not
// contain the substring "%s" at all (the "%" and "s" are separated by "1$"), so positional
// placeholders never get miscounted here.
//
// Run:
//   java tools/verify-lang-placeholders/VerifyLangPlaceholders.java
// (no classpath needed -- this reads the generated JSON files directly with a tiny hand-rolled
// parser, no Gson dependency, so it also works if src/generated hasn't been compiled into
// build/classes/java/main yet)

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerifyLangPlaceholders {

    private static final String[] LOCALES = {
            "en_us", "zh_tw", "zh_cn", "ja_jp", "es_es", "fr_fr", "ko_kr", "ru_ru", "de_de", "pt_br"
    };

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    // Minimal flat-object JSON string parser -- good enough for this file's own shape
    // ({"key": "value", ...}, no nesting), avoids requiring Gson on the classpath.
    private static Map<String, String> parseFlatJsonObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        Pattern entry = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = entry.matcher(json);
        while (m.find()) {
            result.put(unescape(m.group(1)), unescape(m.group(2)));
        }
        return result;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
    }

    private static int countBareS(String value) {
        int count = 0, idx = 0;
        while ((idx = value.indexOf("%s", idx)) != -1) {
            count++;
            idx += 2;
        }
        return count;
    }

    public static void main(String[] args) throws IOException {
        Path base = Path.of("src/generated/resources/assets/microdaerystranslator/lang");

        Map<String, Map<String, String>> byLocale = new LinkedHashMap<>();
        for (String locale : LOCALES) {
            Path file = base.resolve(locale + ".json");
            assertTrue(locale + ".json exists (run ./gradlew runData if this fails)", Files.isRegularFile(file));
            Map<String, String> parsed = parseFlatJsonObject(Files.readString(file, StandardCharsets.UTF_8));
            assertTrue(locale + ".json has a non-trivial number of keys (parser sanity check)", parsed.size() > 50);
            byLocale.put(locale, parsed);
        }

        // Every locale must have the exact same key SET -- a prerequisite for the %s-count
        // comparison below to even make sense (comparing counts for a key that's missing from one
        // locale entirely is undefined, not "0").
        Map<String, String> enUs = byLocale.get("en_us");
        for (String locale : LOCALES) {
            assertTrue(locale + " has exactly the same key set as en_us",
                    byLocale.get(locale).keySet().equals(enUs.keySet()));
        }

        // The real invariant: for each key, the %s count must be identical across all 10 locales.
        int mismatchedKeys = 0;
        for (String key : enUs.keySet()) {
            Map<Integer, String> countToOneExampleLocale = new TreeMap<>();
            for (String locale : LOCALES) {
                int n = countBareS(byLocale.get(locale).get(key));
                countToOneExampleLocale.putIfAbsent(n, locale);
            }
            if (countToOneExampleLocale.size() > 1) {
                System.out.println("MISMATCH " + key + ": %s counts differ across locales -> " + countToOneExampleLocale);
                mismatchedKeys++;
            }
        }
        assertTrue("every key has the same number of bare %s placeholders across all " + LOCALES.length + " locales",
                mismatchedKeys == 0);

        // Sanity checks on the counting method itself.
        assertTrue("countBareS treats '%1$s' as zero bare %s occurrences (positional syntax isn't miscounted)",
                countBareS("foo %1$s bar %1$s") == 0);
        assertTrue("countBareS correctly counts a single legitimate %s",
                countBareS("HTTP Error %s") == 1);
        assertTrue("countBareS correctly counts two consistent-across-locales %s (would be fine if EVERY locale agreed)",
                countBareS("%s and %s") == 2);

        System.out.println("ALL CHECKS PASSED");
    }
}
