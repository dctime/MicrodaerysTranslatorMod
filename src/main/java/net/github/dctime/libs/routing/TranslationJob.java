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
public final class TranslationJob {
    private final TranslationCacheKey key;
    private final String prompt;
    private final String imageBase64;
    private final boolean isScreenshot;
    private final VisionRequirement visionRequirement;

    public TranslationJob(TranslationCacheKey key, String prompt, @Nullable String imageBase64, boolean isScreenshot, VisionRequirement visionRequirement) {
        this.key = key;
        this.prompt = prompt;
        this.imageBase64 = imageBase64;
        this.isScreenshot = isScreenshot;
        this.visionRequirement = visionRequirement;
    }

    public TranslationCacheKey key() {
        return key;
    }

    public String prompt() {
        return prompt;
    }

    @Nullable
    public String imageBase64() {
        return imageBase64;
    }

    public boolean isScreenshot() {
        return isScreenshot;
    }

    public VisionRequirement visionRequirement() {
        return visionRequirement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranslationJob other = (TranslationJob) o;
        return java.util.Objects.equals(key, other.key)
                && java.util.Objects.equals(prompt, other.prompt)
                && java.util.Objects.equals(imageBase64, other.imageBase64)
                && isScreenshot == other.isScreenshot
                && java.util.Objects.equals(visionRequirement, other.visionRequirement);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key, prompt, imageBase64, isScreenshot, visionRequirement);
    }

    @Override
    public String toString() {
        return "TranslationJob[key=" + key + ", prompt=" + prompt + ", imageBase64=" + imageBase64 + ", isScreenshot=" + isScreenshot + ", visionRequirement=" + visionRequirement + "]";
    }
}
