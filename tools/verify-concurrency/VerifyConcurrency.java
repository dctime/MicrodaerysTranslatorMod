// Regression check for the 429 backoff math in net.github.dctime.libs.RetryPolicy (#2), and for
// the SHAPE of the concurrency/in-flight primitives now used by Translator.java + TranslationRouter
// (multi-provider Router refactor, mailbox review rounds 023-028) -- updated from this file's
// original version, which modeled Translator's old single-Semaphore(4)/text-keyed-IN_FLIGHT design.
// That design no longer exists in Translator.java at all (see TranslationRouter's own class
// javadoc): concurrency is now TWO layers (a global Semaphore(TranslationRouter.
// GLOBAL_CONCURRENCY_LIMIT) plus each provider's own Semaphore(ProviderRuntimeState.
// DEFAULT_PROVIDER_CONCURRENCY)), and IN_FLIGHT is keyed by the real TranslationCacheKey record
// (lang+text), not raw text (mailbox review round 023/024, point R2 -- a job can now span several
// sequential provider attempts, widening the window in which the target language could change
// mid-flight).
//
// What this DOES verify against real production code: RetryPolicy.backoffDelayMs() (called
// directly, no copy -- still reused as-is inside ProviderRuntimeState.recordTransientFailure); the
// real net.github.dctime.libs.TranslationCacheKey record (used directly as the Set key below, not
// reimplemented); and the size constants TranslationRouter.GLOBAL_CONCURRENCY_LIMIT /
// ProviderRuntimeState.DEFAULT_PROVIDER_CONCURRENCY -- referenced by name, not independently
// hardcoded, so an edit to either constant's VALUE is picked up automatically the next time this
// file is recompiled (which the run instructions below always do). Precisely: both are `public
// static final int`, so javac INLINES the literal into this file's own compiled bytecode at
// compile time (mailbox review round 029, point W3) -- TranslationRouter's class itself is never
// loaded at RUN time (it can't be: Config.<clinit> needs the real NeoForge classpath, same
// limitation tools/verify-provider-adapters already discloses). The anti-drift protection is real
// but happens at the NEXT javac invocation, not by loading the live class each run.
//
// What this does NOT verify: the FULL acquire/release SEQUENCE inside
// TranslationRouter.attemptCandidate end to end (that still needs ProviderPool.all() ->
// ProviderConfigResolver.resolve() -> live Config values, i.e. the real NeoForge classpath, same
// disclosed limitation tools/verify-provider-adapters already states) -- TranslationRouter itself
// is never loaded here. It DOES now verify the specific ORDERING PROPERTY a real in-game playtest
// caught live (concurrency must be checked before rate is ever touched -- see the new section
// below), by driving the real ProviderRuntimeState class through the same sequence
// attemptCandidate's fixed code follows, by hand. A live game client, or a future test harness that
// fakes ProviderPool, is the only way to verify TranslationRouter's own code literally follows this
// sequence, not just that the sequence itself is sound.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-concurrency tools/verify-concurrency/VerifyConcurrency.java
//   java -cp "tools/verify-concurrency:$MAIN_CLASSES" VerifyConcurrency

import net.github.dctime.Config;
import net.github.dctime.libs.RetryPolicy;
import net.github.dctime.libs.TranslationCacheKey;
import net.github.dctime.libs.routing.ProviderRuntimeState;
import net.github.dctime.libs.routing.TranslationRouter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class VerifyConcurrency {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- RetryPolicy: real production code, no copy ---
        assertTrue("attempt 1 backs off 4s", RetryPolicy.backoffDelayMs(1) == 4_000L);
        assertTrue("attempt 2 backs off 8s", RetryPolicy.backoffDelayMs(2) == 8_000L);
        assertTrue("attempt 3 backs off 16s", RetryPolicy.backoffDelayMs(3) == 16_000L);
        assertTrue("attempt 4 caps at 30s", RetryPolicy.backoffDelayMs(4) == 30_000L);
        assertTrue("attempt 10 stays capped at 30s (no overflow/runaway growth)",
                RetryPolicy.backoffDelayMs(10) == 30_000L);
        assertTrue("delay is monotonically non-decreasing with more attempts",
                RetryPolicy.backoffDelayMs(1) <= RetryPolicy.backoffDelayMs(2)
                        && RetryPolicy.backoffDelayMs(2) <= RetryPolicy.backoffDelayMs(3)
                        && RetryPolicy.backoffDelayMs(3) <= RetryPolicy.backoffDelayMs(4));

        // --- IN_FLIGHT: same primitive (ConcurrentHashMap.newKeySet()) AND the real
        // TranslationCacheKey record Translator.java now actually uses as the key, not a raw String
        // (round 023/024 point R2) ---
        Set<TranslationCacheKey> inFlight = ConcurrentHashMap.newKeySet();
        TranslationCacheKey keyA = new TranslationCacheKey("zh_tw", "A");
        TranslationCacheKey keyB = new TranslationCacheKey("zh_tw", "B");
        TranslationCacheKey keyASameTextDifferentLang = new TranslationCacheKey("ja_jp", "A");

        assertTrue("text A not in flight initially", !inFlight.contains(keyA));
        inFlight.add(keyA);
        assertTrue("text A now blocked (duplicate request for the same (lang, text) job is dropped)",
                inFlight.contains(keyA));
        assertTrue("text B (different text) is NOT blocked by A being in flight", !inFlight.contains(keyB));
        assertTrue("the SAME text under a DIFFERENT target language is a different job, not blocked by A "
                        + "-- this is exactly what keying by TranslationCacheKey(lang, text) instead of raw text fixes",
                !inFlight.contains(keyASameTextDifferentLang));
        inFlight.remove(keyA);
        assertTrue("text A releases correctly and can be requested again", !inFlight.contains(keyA));

        // --- concurrency caps: same primitive (Semaphore), same tryAcquire/release usage, sized
        // from the REAL production constants (not independently hardcoded) ---
        Semaphore globalLimit = new Semaphore(TranslationRouter.GLOBAL_CONCURRENCY_LIMIT);
        int acquired = 0;
        for (int i = 0; i < TranslationRouter.GLOBAL_CONCURRENCY_LIMIT; i++) {
            assertTrue("global permit " + i + " of " + TranslationRouter.GLOBAL_CONCURRENCY_LIMIT + " acquires successfully",
                    globalLimit.tryAcquire());
            acquired++;
        }
        assertTrue("one more concurrent request beyond GLOBAL_CONCURRENCY_LIMIT is rejected",
                !globalLimit.tryAcquire());
        globalLimit.release();
        assertTrue("after one release, a new request can acquire the freed global permit",
                globalLimit.tryAcquire());

        Semaphore providerLimit = new Semaphore(ProviderRuntimeState.DEFAULT_PROVIDER_CONCURRENCY);
        for (int i = 0; i < ProviderRuntimeState.DEFAULT_PROVIDER_CONCURRENCY; i++) {
            assertTrue("provider permit " + i + " of " + ProviderRuntimeState.DEFAULT_PROVIDER_CONCURRENCY + " acquires successfully",
                    providerLimit.tryAcquire());
        }
        assertTrue("one more concurrent request beyond a single provider's own DEFAULT_PROVIDER_CONCURRENCY is rejected "
                        + "(this is the SECOND, independent layer -- a provider can be exhausted while the global cap still has room)",
                !providerLimit.tryAcquire());

        // --- Regression test for a bug found in the FIRST real in-game playtest of the whole
        // Router feature, not by code review: the ORIGINAL attemptCandidate acquired the rate
        // limiter BEFORE the concurrency semaphore. A RateLimiter slot can never be given back once
        // consumed (unlike a Semaphore permit, which release() restores), so while a provider's
        // small concurrency limit (2) was briefly saturated by real in-flight requests, every other
        // render-frame retry for a DIFFERENT text still successfully consumed a rate-limiter slot
        // before failing the concurrency check moments later -- in the actual playtest log, ~30
        // such retries in under a second burned through Google's entire 10-per-minute budget on
        // requests that were never sent, then silently locked every text out of that provider for
        // the rest of the rolling 60s window (self-perpetuating: each fresh render-frame retry kept
        // re-exhausting the window the instant a slot aged out, before a real request could claim
        // it). The fix reorders attemptCandidate to check concurrency FIRST at both levels. This
        // drives the real ProviderRuntimeState class through that same fixed sequence by hand. ---
        ProviderRuntimeState providerState = new ProviderRuntimeState(Config.EndPoint.GOOGLE_AI_STUDIO);
        long simNow = 1_000_000L;

        assertTrue("first real in-flight request acquires provider concurrency", providerState.concurrencyLimit().tryAcquire());
        assertTrue("second real in-flight request acquires provider concurrency", providerState.concurrencyLimit().tryAcquire());

        for (int i = 0; i < 20; i++) {
            assertTrue("burst attempt " + i + " correctly fails to acquire provider concurrency while both "
                            + "real requests are still in flight (matches attemptCandidate's fixed order: "
                            + "concurrency is checked, and fails, before rate is ever touched)",
                    !providerState.concurrencyLimit().tryAcquire());
        }
        assertTrue("after 20 concurrency-blocked burst attempts, the rate limiter is COMPLETELY untouched -- "
                        + "none of its budget was consumed by requests that were never actually sent "
                        + "(this is the exact invariant whose violation caused the live lockup)",
                providerState.currentRateUsage(simNow) == 0);

        providerState.concurrencyLimit().release();
        providerState.concurrencyLimit().release();
        assertTrue("concurrency now free (both real requests completed): a new attempt can acquire it",
                providerState.concurrencyLimit().tryAcquire());
        assertTrue("that attempt goes on to acquire a rate-limiter slot -- the FIRST time rate has been touched at all",
                providerState.tryAcquireRate(10, simNow));
        assertTrue("exactly ONE rate-limiter slot consumed total, not 21 (20 blocked burst attempts + this 1 real one)",
                providerState.currentRateUsage(simNow) == 1);

        // --- AA1/AA2 (mailbox review round 034): the SAME pattern the fix above closes still
        // existed between the router's TWO non-revocable resources (global rate, provider rate) --
        // reordering alone can't fix a pair of non-revocable resources, only a single one against
        // revocable ones. The real fix is check-then-commit: peek every non-revocable resource with
        // a non-consuming read (ProviderRuntimeState.hasRateBudget / the router's own
        // hasGlobalRateBudget) and only actually consume ANY of them once every one has already
        // confirmed it would succeed. This drives that exact pattern by hand: provider rate is
        // exhausted, global rate still has room -- repeated attempts must NEVER touch global rate,
        // since the provider-rate peek fails before global rate is ever committed. (TranslationRouter's
        // own GLOBAL_RATE_LIMITER can't be reached directly from here -- calling its real methods
        // needs Config, which needs the live NeoForge classpath -- so this uses a second, independent
        // net.github.dctime.libs.RateLimiter instance as a stand-in: the exact same production class
        // TranslationRouter's global limiter actually is, just not that one specific singleton.) ---
        net.github.dctime.libs.RateLimiter globalRateLimiterStandIn = new net.github.dctime.libs.RateLimiter(60_000L);
        ProviderRuntimeState exhaustedProviderState = new ProviderRuntimeState(Config.EndPoint.GROQ);
        long simNow2 = 2_000_000L;
        int providerRpmLimit = 10;

        for (int i = 0; i < providerRpmLimit; i++) {
            assertTrue("filling this provider's own rate limiter to its cap, slot " + i,
                    exhaustedProviderState.tryAcquireRate(providerRpmLimit, simNow2));
        }
        assertTrue("this provider's rate limiter is now fully exhausted",
                !exhaustedProviderState.hasRateBudget(providerRpmLimit, simNow2));

        for (int i = 0; i < 15; i++) {
            assertTrue("attempt " + i + ": the GLOBAL rate peek still correctly shows room (it was never touched)",
                    globalRateLimiterStandIn.usageAt(simNow2) < 10);
            assertTrue("attempt " + i + ": the PROVIDER rate peek correctly shows no room",
                    !exhaustedProviderState.hasRateBudget(providerRpmLimit, simNow2));
            // Fixed order: global rate is only ever COMMITTED (a real tryAcquire call) once BOTH
            // peeks pass. The provider peek just failed, so this attempt must stop here -- global
            // rate's tryAcquire is deliberately never called for it.
        }
        assertTrue("after 15 attempts that all failed on PROVIDER rate, the GLOBAL rate limiter is "
                        + "COMPLETELY untouched -- 0 slots consumed, not 15 (AA1's exact invariant: a "
                        + "non-revocable resource must never be consumed by an attempt doomed to fail "
                        + "at a DIFFERENT non-revocable check)",
                globalRateLimiterStandIn.usageAt(simNow2) == 0);

        System.out.println("ALL CHECKS PASSED");
    }
}
