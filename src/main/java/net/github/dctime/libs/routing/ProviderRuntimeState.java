package net.github.dctime.libs.routing;

import net.github.dctime.Config;
import net.github.dctime.libs.RateLimiter;
import net.github.dctime.libs.RetryPolicy;

import javax.annotation.Nullable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One long-lived instance per {@link Config.EndPoint} (see {@link ProviderRuntimeRegistry}) --
 * in-flight count, rolling rate-limit window, cooldown, consecutive-failure count, and a rolling
 * average latency. Replaces {@code Translator}'s old single global {@code CONCURRENCY_LIMIT}/
 * {@code REQUEST_RATE_LIMITER}/{@code RETRY_AFTER}/{@code RETRY_ATTEMPTS} (mailbox review round
 * 023, point R1 -- those two maps were keyed by TEXT, meaning a 429 from one provider blocked
 * retries for a completely unrelated text while doing nothing to stop OTHER texts from still being
 * sent to the 429'd provider; this class fixes that by construction, keyed by provider instead).
 * <p>
 * Deliberately NEVER serialized to TOML -- runtime only, rebuilt empty on every game launch, per
 * the spec's explicit instruction. Thread-safe: confirmed from this session's own real
 * {@code latest.log} that HTTP completion callbacks run on {@code ForkJoinPool.commonPool} worker
 * threads, not the render thread, so every mutable field here is either an atomic, volatile, or
 * guarded by a small synchronized block (see {@link #recordSuccess}) -- never a plain field.
 */
public final class ProviderRuntimeState {

    public enum Status { AVAILABLE, RATE_LIMITED, COOLDOWN, CONNECTION_ERROR, AUTH_ERROR }

    /** Not exposed to the player this round (spec: "第一版不需要給玩家改 concurrency") -- a
     *  reasonable per-provider default, independent of (and smaller than) the global safety cap. */
    public static final int DEFAULT_PROVIDER_CONCURRENCY = 2;
    private static final long RATE_LIMIT_WINDOW_MILLIS = 60_000L;

    private final Config.EndPoint endpoint;
    private final RateLimiter rateLimiter = new RateLimiter(RATE_LIMIT_WINDOW_MILLIS);
    private final Semaphore concurrencyLimit = new Semaphore(DEFAULT_PROVIDER_CONCURRENCY);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long cooldownUntilMillis = 0L;
    private volatile boolean authError = false;
    private volatile long lastFailureMillis = 0L;
    private volatile long lastSuccessMillis = 0L;
    @Nullable
    private volatile ProviderFailureType lastFailureType = null;
    private volatile double averageLatencyMs = 0.0;
    private volatile boolean hasLatencySample = false;

    public ProviderRuntimeState(Config.EndPoint endpoint) {
        this.endpoint = endpoint;
    }

    public Config.EndPoint endpoint() {
        return endpoint;
    }

    /** Guarded, not the raw {@link RateLimiter} -- that class documents itself as not thread-safe
     *  (its own javadoc: "Translator only calls this from the render thread"), an assumption HTTP
     *  completion callbacks running on {@code ForkJoinPool} worker threads would violate if callers
     *  touched the deque directly. {@link #tryAcquireRate} and {@link #currentRateCount} are the
     *  only two entry points into it, both synchronized on this instance, so concurrent attempts
     *  from different provider jobs never race on the same provider's window. */
    public synchronized boolean tryAcquireRate(int limit, long nowMillis) {
        return rateLimiter.tryAcquire(limit, nowMillis);
    }

    public synchronized int currentRateCount() {
        return rateLimiter.currentCount();
    }

    /** Evicts expired timestamps first, then reports usage -- the correct read for {@link
     *  ProviderScorer} (see {@link RateLimiter#usageAt}'s javadoc: a provider that hasn't been
     *  acquired from in a while would otherwise report a permanently-stale, inflated count via
     *  {@link #currentRateCount}, since eviction normally only happens inside {@code tryAcquire}). */
    public synchronized int currentRateUsage(long nowMillis) {
        return rateLimiter.usageAt(nowMillis);
    }

    /** Non-consuming peek: would {@link #tryAcquireRate} succeed right now, WITHOUT actually
     *  recording a timestamp. Exists for {@code TranslationRouter.attemptCandidate}'s "check every
     *  non-revocable resource before committing to any of them" rule (mailbox review round 035,
     *  point AA1) -- a {@link RateLimiter} slot can't be given back once consumed, unlike a {@link
     *  java.util.concurrent.Semaphore} permit, so two non-revocable checks (this provider's rate AND
     *  the router's global rate) can only be made safe by peeking both first and committing both
     *  only once neither would fail -- reordering alone (the fix for the FIRST version of this bug)
     *  only works when there's a single non-revocable resource in the chain. */
    public synchronized boolean hasRateBudget(int limit, long nowMillis) {
        return rateLimiter.usageAt(nowMillis) < limit;
    }

    public Semaphore concurrencyLimit() {
        return concurrencyLimit;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public void incrementInFlight() {
        inFlight.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlight.decrementAndGet();
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public double averageLatencyMs() {
        return averageLatencyMs;
    }

    /** False until this provider's first {@link #recordSuccess} -- {@link ProviderScorer} uses this
     *  to distinguish "genuinely never tried" (a neutral prior penalty) from "tried and found
     *  fast" ({@link #averageLatencyMs} legitimately 0 or near it), which {@link #averageLatencyMs}
     *  alone can't tell apart (mailbox review round, cold-start scoring design). */
    public boolean hasLatencySample() {
        return hasLatencySample;
    }

    /** Most recent of {@link #recordSuccess}/{@link #recordTransientFailure}/{@link
     *  #recordAuthFailure}/{@link #recordNonTransientFailure} -- i.e. "when was this provider last
     *  actually attempted, regardless of outcome". 0 if never attempted. {@link ProviderScorer}
     *  uses this to periodically discount a stale bad reputation (a provider that was slow or
     *  failing a while ago gets reconsidered over time, instead of staying permanently deprioritized
     *  by one old data point) -- only meaningful once {@link #hasLatencySample()} is true; a
     *  genuinely untried provider's 0 here is never read for that purpose (see ProviderScorer's own
     *  javadoc for why the two code paths don't overlap). */
    public long lastAttemptMillis() {
        return Math.max(lastFailureMillis, lastSuccessMillis);
    }

    public boolean isInCooldown(long nowMillis) {
        return nowMillis < cooldownUntilMillis;
    }

    public long cooldownRemainingMillis(long nowMillis) {
        return Math.max(0L, cooldownUntilMillis - nowMillis);
    }

    public boolean isAuthError() {
        return authError;
    }

    public void recordSuccess(long latencyMs) {
        consecutiveFailures.set(0);
        cooldownUntilMillis = 0L;
        authError = false;
        lastFailureType = null;
        lastSuccessMillis = System.currentTimeMillis();
        updateLatency(latencyMs);
    }

    private synchronized void updateLatency(long latencyMs) {
        // synchronized, not volatile-only: this is a read-modify-write (EWMA), not a single
        // assignment -- two overlapping successes both computing from a stale averageLatencyMs
        // read would lose an update. Small, cheap, non-blocking critical section.
        if (!hasLatencySample) {
            averageLatencyMs = latencyMs;
            hasLatencySample = true;
        } else {
            averageLatencyMs = 0.8 * averageLatencyMs + 0.2 * latencyMs;
        }
    }

    /** RATE_LIMIT/TIMEOUT/CONNECTION/SERVER: cooldown honors an explicit {@code Retry-After}
     *  duration when the caller has one (429 response header), else falls back to
     *  {@link RetryPolicy#backoffDelayMs}, keyed on THIS PROVIDER's own consecutive-failure count
     *  (that method is already a pure function of attempt count, trivially reusable per-provider). */
    public void recordTransientFailure(ProviderFailureType type, @Nullable Long retryAfterMillis) {
        lastFailureMillis = System.currentTimeMillis();
        lastFailureType = type;
        int attempt = consecutiveFailures.incrementAndGet();
        long delay = retryAfterMillis != null ? retryAfterMillis : RetryPolicy.backoffDelayMs(attempt);
        cooldownUntilMillis = System.currentTimeMillis() + delay;
    }

    /** AUTH: no timer-based cooldown (a timer would imply "will resolve itself"), just a sticky
     *  flag until the player fixes the key or a Test Connection succeeds (see
     *  {@link #clearAuthAndCooldown}). Never erases the saved API key itself -- that's a GUI/Config
     *  concern, out of scope for this class. */
    public void recordAuthFailure() {
        lastFailureMillis = System.currentTimeMillis();
        lastFailureType = ProviderFailureType.AUTH;
        consecutiveFailures.incrementAndGet();
        authError = true;
    }

    /** BAD_REQUEST/UNSUPPORTED_CAPABILITY/MALFORMED_RESPONSE: deliberately does NOT touch
     *  cooldown/consecutiveFailures -- a bad request for one specific text says nothing about this
     *  provider's general health; per-job exclusion (via TranslationAttemptContext) is enough. */
    public void recordNonTransientFailure() {
        lastFailureMillis = System.currentTimeMillis();
    }

    /** Called when the player saves a changed API key for this provider, or a manual Test
     *  Connection succeeds -- immediately clears AUTH_ERROR and any active cooldown so the provider
     *  is eligible again without waiting for a timer or a restart ("Runtime Health Recovery"). */
    public void clearAuthAndCooldown() {
        authError = false;
        cooldownUntilMillis = 0L;
        lastFailureType = null;
        consecutiveFailures.set(0);
    }

    /** Derived, never persisted -- recomputed fresh every call from the fields above. */
    public Status status(long nowMillis) {
        if (authError) return Status.AUTH_ERROR;
        if (isInCooldown(nowMillis)) {
            if (lastFailureType == ProviderFailureType.RATE_LIMIT) return Status.RATE_LIMITED;
            if (lastFailureType == ProviderFailureType.TIMEOUT || lastFailureType == ProviderFailureType.CONNECTION) {
                return Status.CONNECTION_ERROR;
            }
            return Status.COOLDOWN;
        }
        return Status.AVAILABLE;
    }
}
