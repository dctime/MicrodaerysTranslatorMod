package net.github.dctime.libs.routing;

import net.github.dctime.libs.TranslationCacheKey;

import javax.annotation.Nullable;

/**
 * One translation request to route -- everything {@link TranslationRouter} needs, and nothing it
 * needs to go back to {@link net.github.dctime.libs.Translator} for. {@code key} is the job's
 * identity (see {@link TranslationCacheKey}'s javadoc for why it's resolved once, by the caller,
 * not re-derived here); {@code prompt} is already fully resolved (built-in template or player
 * override substitution already applied) -- prompt resolution stays Translator's job, per the
 * spec's own division of responsibility, the router only ever sees the final string.
 */
public record TranslationJob(TranslationCacheKey key, String prompt, @Nullable String imageBase64,
                              boolean isScreenshot, VisionRequirement visionRequirement) {
}
