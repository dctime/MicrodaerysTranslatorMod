package net.github.dctime.libs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Small, closed lookup table for the languages this mod's default prompt/skip-detection
 * actually supports. No Minecraft/NeoForge dependency on purpose (see JsonUtil/RetryPolicy).
 * Deliberately not a pluggable language-detection framework -- only covers what's needed now;
 * an unrecognized language code degrades gracefully (raw code as display name, translation
 * never skipped) instead of throwing.
 *
 * Language codes use Minecraft's own format (e.g. "zh_tw", "ja_jp", "en_us" -- underscore,
 * with region suffix), the same codes LanguageManager.getSelected() and lang/*.json files use,
 * not an invented scheme -- so there's one language-code universe across the whole mod, not two
 * that need translating between each other.
 */
public class TargetLanguage {

    private record Info(String displayName, Pattern alreadyTranslatedPattern) {}

    // LinkedHashMap (not Map.of) so iteration order is stable and deliberate: KNOWN_CODES below
    // derives directly from this map's keys instead of duplicating the list by hand, so "which
    // languages does this mod know about" has exactly one source of truth -- a language added
    // here without updating a second list (e.g. a GUI dropdown) is a class of drift this
    // structurally can't have, the same failure shape Translator.resolveOrRequestTranslation
    // exists to prevent for tooltip lookups.
    // Both declared with no initializer and assigned together in the static block below (not
    // KNOWN via the block + KNOWN_CODES via a field initializer that reads KNOWN) on purpose:
    // JLS textual-order execution WOULD make "KNOWN_CODES = List.copyOf(KNOWN.keySet())" as a
    // field initializer work today, but only for as long as that field initializer stays textually
    // below this block -- an innocuous-looking reorder (e.g. moving KNOWN_CODES up next to KNOWN's
    // declaration) would silently read KNOWN while it's still null and blow up <clinit> with an
    // NPE at class-load time, taking the whole mod down with it. Assigning both in the same block
    // makes the ordering explicit instead of relying on where a field happens to be declared.
    private static final Map<String, Info> KNOWN;
    /** Language codes this mod has a curated Info for, in display order. For GUI dropdowns etc. */
    public static final List<String> KNOWN_CODES;

    static {
        Map<String, Info> known = new LinkedHashMap<>();
        known.put("zh_tw", new Info("繁體中文", Pattern.compile("[一-龥]")));
        known.put("zh_cn", new Info("简体中文", Pattern.compile("[一-龥]")));
        // kanji overlaps the CJK range above, so only hiragana/katakana are used to detect
        // "already Japanese" -- kanji-only Japanese text won't be caught, but that's the same
        // ambiguity kanji has between zh/ja in general, not something worth a bigger heuristic here.
        known.put("ja_jp", new Info("日文", Pattern.compile("[぀-ヿ]")));
        known.put("en_us", new Info("English", Pattern.compile("^[\\x00-\\x7F]*$")));
        // Spanish/French share most of the Latin alphabet with English (only a handful of
        // accented characters differ), so an ASCII-range check like en_us's would misfire
        // constantly -- plain English text would look "already Spanish/French". Rather than
        // ship an inaccurate heuristic, these never report "already translated" (same safe
        // default as an unknown language code): a few redundant translation calls beat
        // silently skipping text that still needs translating.
        known.put("es_es", new Info("Español", Pattern.compile("(?!)")));
        known.put("fr_fr", new Info("Français", Pattern.compile("(?!)")));
        // Korean (Hangul) and Russian (Cyrillic) each have their own dedicated Unicode block, so
        // -- like zh/ja above -- a script-range check is a real, low-false-positive signal.
        known.put("ko_kr", new Info("한국어", Pattern.compile("[가-힣]")));
        known.put("ru_ru", new Info("Русский", Pattern.compile("[Ѐ-ӿ]")));
        // German and Portuguese share the plain Latin alphabet with English (only a handful of
        // accented/special characters differ: ä ö ü ß / ã õ ç), same ambiguity as es_es/fr_fr
        // above -- never report "already translated" rather than ship an inaccurate heuristic.
        known.put("de_de", new Info("Deutsch", Pattern.compile("(?!)")));
        known.put("pt_br", new Info("Português (Brasil)", Pattern.compile("(?!)")));
        KNOWN = Collections.unmodifiableMap(known);
        KNOWN_CODES = List.copyOf(known.keySet());
    }

    public static String displayName(String languageCode) {
        Info info = KNOWN.get(normalize(languageCode));
        return info != null ? info.displayName() : languageCode;
    }

    /** Best-effort: is this text already written in the target language (so translation would be redundant)? */
    public static boolean isAlreadyInTargetLanguage(String languageCode, String text) {
        if (text == null) return false;
        Info info = KNOWN.get(normalize(languageCode));
        if (info == null) return false; // unknown language code: never skip, always attempt translation
        return info.alreadyTranslatedPattern().matcher(text).find();
    }

    private static String normalize(String languageCode) {
        return languageCode == null ? "" : languageCode.trim().toLowerCase();
    }
}
