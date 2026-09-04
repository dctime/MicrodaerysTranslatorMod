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
public record TranslationCacheKey(String lang, String text) {
}
