package net.github.dctime.libs;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple sliding-window rate limiter: at most {@code limit} calls to {@link #tryAcquire} succeed
 * within any rolling window of {@code windowMillis}. The limit is passed in per-call (not fixed
 * in the constructor) so a caller can read it fresh from a live config value every time, rather
 * than freezing whatever it was when this object was constructed.
 *
 * Not thread-safe by itself -- Translator only calls this from the render thread, so it doesn't
 * need to be. No Minecraft dependency; "now" is passed in explicitly so this is testable without
 * depending on the wall clock (see tools/verify-rate-limiter).
 */
public class RateLimiter {
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();
    private final long windowMillis;

    public RateLimiter(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public boolean tryAcquire(int limit, long nowMillis) {
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() <= nowMillis - windowMillis) {
            requestTimestamps.pollFirst();
        }
        if (requestTimestamps.size() >= limit) return false;
        requestTimestamps.addLast(nowMillis);
        return true;
    }

    /** How many acquisitions are currently counted within the window, as of the last tryAcquire() call. */
    public int currentCount() {
        return requestTimestamps.size();
    }
}
