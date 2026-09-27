// Regression check for net.github.dctime.libs.RateLimiter (#17: a real per-minute request
// throttle on top of the existing concurrency cap, added after a real player hit 429s opening a
// container -- CONCURRENCY_LIMIT (Semaphore(4)) only bounds how many requests are in flight at
// once, not how many get dispatched over a rolling 60s window, which is what a free API tier's
// RPM quota actually restricts).
//
// RateLimiter is pure (no Minecraft dependency, "now" passed in explicitly), so this calls the
// REAL production class directly -- no copy, no headless-limitation caveat needed here.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-rate-limiter tools/verify-rate-limiter/VerifyRateLimiter.java
//   java -cp "tools/verify-rate-limiter:$MAIN_CLASSES" VerifyRateLimiter

import net.github.dctime.libs.RateLimiter;

public class VerifyRateLimiter {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- basic cap within the window ---
        RateLimiter limiter = new RateLimiter(60_000L);
        long t0 = 1_000_000L;
        for (int i = 0; i < 4; i++) {
            assertTrue("request " + (i + 1) + " of 4 (limit=4) is allowed", limiter.tryAcquire(4, t0));
        }
        assertTrue("the 5th request within the same instant is rejected once the limit of 4 is reached",
                !limiter.tryAcquire(4, t0));
        assertTrue("currentCount() reflects the 4 accepted requests", limiter.currentCount() == 4);

        // --- sliding window: entries older than windowMillis age out, freeing up budget ---
        RateLimiter slidingLimiter = new RateLimiter(60_000L);
        assertTrue("t=0: first request allowed", slidingLimiter.tryAcquire(2, 0L));
        assertTrue("t=0: second request allowed (limit=2)", slidingLimiter.tryAcquire(2, 0L));
        assertTrue("t=30_000 (still within the 60s window): third request rejected, limit still full",
                !slidingLimiter.tryAcquire(2, 30_000L));
        assertTrue("t=60_001 (the t=0 requests are now outside the 60s window): a new request is allowed again",
                slidingLimiter.tryAcquire(2, 60_001L));
        assertTrue("t=60_001: both t=0 entries aged out together in the previous call, so a second new request is also allowed here",
                slidingLimiter.tryAcquire(2, 60_001L));
        assertTrue("t=60_001: now the limit of 2 is full again for this instant",
                !slidingLimiter.tryAcquire(2, 60_001L));

        // --- a request exactly windowMillis old is considered expired (boundary is inclusive of "now - window") ---
        RateLimiter boundaryLimiter = new RateLimiter(60_000L);
        assertTrue("t=0: first request allowed", boundaryLimiter.tryAcquire(1, 0L));
        assertTrue("t=60_000 exactly: the t=0 entry has aged out (60_000 <= 60_000 - 60_000 is false... "
                        + "checked the other direction: entry timestamp 0 <= now(60000) - window(60000) = 0 -> expired)",
                boundaryLimiter.tryAcquire(1, 60_000L));

        // --- changing the limit between calls takes effect immediately (this is WHY the limit is
        // a tryAcquire() parameter, not baked into the constructor -- Config.MAX_REQUESTS_PER_MINUTE
        // can change live via the config screen) ---
        RateLimiter dynamicLimiter = new RateLimiter(60_000L);
        assertTrue("t=0: request allowed under limit=1", dynamicLimiter.tryAcquire(1, 0L));
        assertTrue("t=0: a second request is rejected while the limit is still 1",
                !dynamicLimiter.tryAcquire(1, 0L));
        assertTrue("t=0: the SAME limiter instance immediately allows more once called with a higher limit",
                dynamicLimiter.tryAcquire(5, 0L));

        // --- currentCount() is stale unless tryAcquire has run recently (mailbox review round 026,
        // point U1): an idle provider that nobody calls tryAcquire on again never gets its old
        // timestamps evicted, since eviction only happens inside tryAcquire. usageAt() must NOT
        // have that problem -- it evicts on its own even with zero tryAcquire calls in between. ---
        RateLimiter staleLimiter = new RateLimiter(60_000L);
        assertTrue("t=0: fill the window (limit=3)", staleLimiter.tryAcquire(3, 0L));
        assertTrue("t=0: fill the window (limit=3)", staleLimiter.tryAcquire(3, 0L));
        assertTrue("t=0: fill the window (limit=3)", staleLimiter.tryAcquire(3, 0L));
        assertTrue("t=0: currentCount() correctly reports 3 right after filling",
                staleLimiter.currentCount() == 3);
        assertTrue("t=120_000 (well past the 60s window), with NO tryAcquire call in between: "
                        + "currentCount() is STALE and still reports the old count of 3 -- this is the bug U1 warns about",
                staleLimiter.currentCount() == 3);
        assertTrue("t=120_000, same limiter, same lack of any tryAcquire call in between: "
                        + "usageAt() evicts on its own and correctly reports 0",
                staleLimiter.usageAt(120_000L) == 0);
        assertTrue("after calling usageAt(), currentCount() now also reports 0 (usageAt's eviction is real, not read-only)",
                staleLimiter.currentCount() == 0);

        System.out.println("ALL CHECKS PASSED");
    }
}
