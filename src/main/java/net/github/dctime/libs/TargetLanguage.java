package net.github.dctime.libs;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Small, closed lookup table for the languages this mod's default prompt/skip-detection
 * actually supports. No Minecraft/NeoForge dependency on purpose (see JsonUtil/RetryPolicy).
 * Deliberately not a pluggable language-detection framework -- only covers what's needed now;
 * an unrecognized language code degrades gracefully (raw code as display name, translation
 * never skipped) instead of throwing.
 */
public class TargetLanguage {

    private record Info(String displayName, Pattern alreadyTranslatedPattern) {}

    private static final Map<String, Info> KNOWN = Map.of(
            "zh-tw", new Info("繁體中文", Pattern.compile("[一-龥]")),
            "zh-cn", new Info("简体中文", Pattern.compile("[一-龥]")),
            // kanji overlaps the CJK range above, so only hiragana/katakana are used to detect
            // "already Japanese" -- kanji-only Japanese text won't be caught, but that's the same
            // ambiguity kanji has between zh/ja in general, not something worth a bigger heuristic here.
            "ja", new Info("日文", Pattern.compile("[぀-ヿ]")),
            "en", new Info("English", Pattern.compile("^[\\x00-\\x7F]*$"))
    );

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
