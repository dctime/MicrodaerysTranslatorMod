package net.github.dctime.libs.routing;

import net.github.dctime.Config;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-job bookkeeping for {@link TranslationRouter}'s fallback loop -- NOT shared across jobs, a
 * fresh instance per {@link TranslationRouter#translate} call. Tracks which providers this exact
 * job has already tried (each is tried at most once per job -- no "Google -> Groq -> Google -> Groq"
 * loop) and a hard ceiling on total attempts as an extra safety net independent of pool size.
 * <p>
 * Deliberately backed by a plain (non-thread-safe) {@link EnumSet}, not a concurrent collection:
 * attempts within one job are strictly SEQUENTIAL (the router never starts attempt N+1 until
 * attempt N's {@code whenComplete} has run), even though different attempts may execute on
 * different {@code ForkJoinPool} worker threads. That's still safe -- a {@code CompletableFuture}
 * stage's completion happens-before the next chained stage's execution (a JMM guarantee of the
 * {@code java.util.concurrent} framework itself), which is exactly the same relationship that makes
 * plain fields safe to hand off between a producer and consumer thread via a queue. This class must
 * NOT be shared or read from a second concurrent attempt -- it never is, by construction, but if
 * that constraint is ever broken this stops being safe.
 */
public final class TranslationAttemptContext {

    /** Named per the spec's explicit request for this concept, even though {@link #attemptedProviders}
     *  already bounds total attempts to at most the pool size (max 11) on its own -- this is a
     *  defensive ceiling, not the primary mechanism. */
    public static final int MAX_PROVIDER_ATTEMPTS = 5;

    private final Set<Config.EndPoint> attemptedProviders = EnumSet.noneOf(Config.EndPoint.class);

    @Nullable
    private ProviderFailureType lastFailureType;

    /** Most recent classified failure across every attempt so far in this job -- lets {@link
     *  TranslationRouter} report a meaningful {@code finalFailure} when the eligible pool finally
     *  comes up empty (e.g. "the last thing that actually happened was a 429"), instead of a bare
     *  null that tells {@code Translator} nothing about what to show the player. Same sequential-
     *  access guarantee as {@link #attemptedProviders} applies -- see the class javadoc. */
    public void recordFailure(ProviderFailureType type) {
        this.lastFailureType = type;
    }

    @Nullable
    public ProviderFailureType lastFailureType() {
        return lastFailureType;
    }

    public boolean hasAttempted(Config.EndPoint endpoint) {
        return attemptedProviders.contains(endpoint);
    }

    public void markAttempted(Config.EndPoint endpoint) {
        attemptedProviders.add(endpoint);
    }

    public int attemptCount() {
        return attemptedProviders.size();
    }

    public boolean reachedMaxAttempts() {
        return attemptedProviders.size() >= MAX_PROVIDER_ATTEMPTS;
    }
}
