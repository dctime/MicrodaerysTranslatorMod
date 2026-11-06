package net.github.dctime.libs;

/**
 * Identity of one translation: target language + source text. A record has built-in
 * equals/hashCode, so there's no string-concatenation-with-a-delimiter to accidentally collide
 * with real content.
 * <p>
 * Also doubles as a translation JOB's identity for {@link net.github.dctime.libs.routing.TranslationRouter}
 * (see {@code TranslationJob}) -- was previously a private nested record inside {@link Translator}
 * used only for the on-memory cache map; promoted to a shared top-level type (mailbox review round
 * 024/023, point R2) specifically so {@code Translator.IN_FLIGHT} and the eventual cache write-back
 * use the EXACT SAME value, resolved exactly once when a job starts, rather than being re-derived
 * from live config (e.g. {@code resolveTargetLanguage()}) at multiple points in time. Before the
 * router existed, a translation's lifetime was one HTTP round-trip, short enough that the target
 * language changing mid-flight was a non-issue; a router job can now span several sequential
 * provider attempts, widening that window enough to matter -- resolving this once and threading it
 * through unchanged closes that gap by construction instead of by discipline.
 */
public final class TranslationCacheKey {
    private final String lang;
    private final String text;

    public TranslationCacheKey(String lang, String text) {
        this.lang = lang;
        this.text = text;
    }

    public String lang() {
        return lang;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranslationCacheKey other = (TranslationCacheKey) o;
        return java.util.Objects.equals(lang, other.lang)
                && java.util.Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(lang, text);
    }

    @Override
    public String toString() {
        return "TranslationCacheKey[lang=" + lang + ", text=" + text + "]";
    }
}
