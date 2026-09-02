package net.github.dctime.libs;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple sliding-window rate limiter: at most {@code limit} calls to {@link #tryAcquire} succeed
 * within any rolling window of {@code windowMillis}. The limit is passed in per-call (not fixed
 * in the constructor) so a caller can read it fresh from a live config value every time, rather
 * than freezing whatever it was when this object was constructed.
 *
 * Not thread-safe by itself -- the caller is responsible for serializing access (see {@code
 * ProviderRuntimeState}, which guards every access to its own instance with synchronized wrapper
 * methods; that stopped being optional once {@code TranslationRouter}'s fallback loop started
 * calling in from HTTP client threads via {@code whenComplete}, not just the render thread). No
 * Minecraft dependency; "now" is passed in explicitly so this is testable without depending on the
 * wall clock (see tools/verify-rate-limiter).
 */
public class RateLimiter {
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();
    private final long windowMillis;

    public RateLimiter(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public boolean tryAcquire(int limit, long nowMillis) {
        evictExpired(nowMillis);
        if (requestTimestamps.size() >= limit) return false;
        requestTimestamps.addLast(nowMillis);
        return true;
    }

    /** How many acquisitions are currently counted within the window, as of the last tryAcquire()
     *  call -- STALE unless something has called tryAcquire recently: an idle provider's old
     *  timestamps never get evicted on their own, since eviction only runs inside tryAcquire. Use
     *  {@link #usageAt} instead for anything that reads usage without necessarily also acquiring
     *  (e.g. scoring an idle candidate) -- this method is kept only for tryAcquire's own callers
     *  that already know an acquire just happened. */
    public int currentCount() {
        return requestTimestamps.size();
    }

    /** Same eviction {@code tryAcquire} does, but without also attempting to acquire -- the correct
     *  way to inspect current usage for a provider that might not have been called in a while (an
     *  idle provider's stale timestamps would otherwise never expire, since nothing evicts them
     *  except tryAcquire itself; a scorer reading raw {@link #currentCount} on such a provider would
     *  see a permanently-inflated count and could never rank it back up -- a self-reinforcing
     *  starvation, not just a stale read). Mutates (evicts), so callers must serialize with {@link
     *  #tryAcquire} the same way. */
    public int usageAt(long nowMillis) {
        evictExpired(nowMillis);
        return requestTimestamps.size();
    }

    private void evictExpired(long nowMillis) {
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() <= nowMillis - windowMillis) {
            requestTimestamps.pollFirst();
        }
    }
}
