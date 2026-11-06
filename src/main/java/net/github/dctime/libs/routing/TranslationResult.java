package net.github.dctime.libs.routing;

import net.github.dctime.Config;

import javax.annotation.Nullable;

/**
 * Outcome of {@link TranslationRouter#translate}. {@code translatedText == null} means the job
 * cleanly failed (every eligible provider was tried and failed, or none were eligible at all) --
 * NOT an exceptional/crashed state. The returned {@code CompletableFuture} always completes
 * normally for every expected failure path; exceptional completion is reserved for genuine bugs
 * that should never actually happen in the tested code paths, matching the spec's explicit
 * "cleanly fails... 不 crash" requirement.
 *
 * @param translatedText null on a clean failure.
 * @param providerUsed   which provider actually produced {@code translatedText}; null on failure.
 * @param latencyMs      wall-clock time of the attempt that produced the result (or the last
 *                       attempted one, on failure) -- diagnostics only, never written to the cache.
 * @param finalFailure   why the job failed; null on success. Lets {@code Translator} pick a
 *                       reasonable chat message without needing to know routing internals.
 */
public final class TranslationResult {
    private final String translatedText;
    private final Config.EndPoint providerUsed;
    private final long latencyMs;
    private final ProviderFailureType finalFailure;

    public TranslationResult(@Nullable String translatedText, @Nullable Config.EndPoint providerUsed, long latencyMs, @Nullable ProviderFailureType finalFailure) {
        this.translatedText = translatedText;
        this.providerUsed = providerUsed;
        this.latencyMs = latencyMs;
        this.finalFailure = finalFailure;
    }

    @Nullable
    public String translatedText() {
        return translatedText;
    }

    @Nullable
    public Config.EndPoint providerUsed() {
        return providerUsed;
    }

    public long latencyMs() {
        return latencyMs;
    }

    @Nullable
    public ProviderFailureType finalFailure() {
        return finalFailure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranslationResult other = (TranslationResult) o;
        return java.util.Objects.equals(translatedText, other.translatedText)
                && java.util.Objects.equals(providerUsed, other.providerUsed)
                && latencyMs == other.latencyMs
                && java.util.Objects.equals(finalFailure, other.finalFailure);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(translatedText, providerUsed, latencyMs, finalFailure);
    }

    @Override
    public String toString() {
        return "TranslationResult[translatedText=" + translatedText + ", providerUsed=" + providerUsed + ", latencyMs=" + latencyMs + ", finalFailure=" + finalFailure + "]";
    }

    public static TranslationResult success(String translatedText, Config.EndPoint providerUsed, long latencyMs) {
        return new TranslationResult(translatedText, providerUsed, latencyMs, null);
    }

    public static TranslationResult failure(@Nullable ProviderFailureType finalFailure) {
        return new TranslationResult(null, null, 0, finalFailure);
    }

    public boolean succeeded() {
        return translatedText != null;
    }
}
