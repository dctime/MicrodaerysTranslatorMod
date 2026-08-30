// Regression check for the per-key concurrency model in Translator.java (#1: replacing the old
// single global `translating` lock) and the 429 backoff math in
// net.github.dctime.libs.RetryPolicy (#2).
//
// What this DOES verify against real production code: RetryPolicy.backoffDelayMs() is called
// directly (no copy), since RetryPolicy has no Minecraft/NeoForge dependency.
//
// What this does NOT verify: Translator.requestTranslateToTraditionalChinese() itself cannot be
// exercised here. Unlike JsonUtil, Translator has a static field initializer
// (`translatedStyle = Style.EMPTY.withColor(...)`) that touches Minecraft classes, so simply
// *loading* the Translator class triggers <clinit> and throws NoClassDefFoundError outside the
// full game runtime -- there's no way to call even an unrelated static method on it headless.
// The IN_FLIGHT / CONCURRENCY_LIMIT check-then-act ordering below is instead verified by
// reproducing the exact same sequence of calls against the same JDK primitives
// (ConcurrentHashMap.newKeySet(), Semaphore) that Translator.java uses, in the same order as the
// diff. That proves the state machine's intended behaviour, not that Translator.java's actual
// bytecode matches it line for line -- if Translator.java's ordering drifts from this later,
// this check won't catch it. A live game client is the only way to close that gap; see also the
// disclosed limitation in tools/verify-json-escaping.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-concurrency tools/verify-concurrency/VerifyConcurrency.java
//   java -cp "tools/verify-concurrency:$MAIN_CLASSES" VerifyConcurrency

import net.github.dctime.libs.RetryPolicy;

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

        // --- per-key in-flight tracking: same primitive (ConcurrentHashMap.newKeySet()),
        // same check-then-act sequence as Translator.requestTranslateToTraditionalChinese ---
        Set<String> inFlight = ConcurrentHashMap.newKeySet();

        assertTrue("text A not in flight initially", !inFlight.contains("A"));
        inFlight.add("A");
        assertTrue("text A now blocked (duplicate request for the same text is dropped)",
                inFlight.contains("A"));
        assertTrue("text B (different text) is NOT blocked by A being in flight",
                !inFlight.contains("B"));
        inFlight.remove("A");
        assertTrue("text A releases correctly and can be requested again", !inFlight.contains("A"));

        // --- global concurrency cap: same primitive (Semaphore), same tryAcquire/release usage ---
        Semaphore limit = new Semaphore(4);
        int acquired = 0;
        for (int i = 0; i < 4; i++) {
            assertTrue("permit " + i + " of 4 acquires successfully", limit.tryAcquire());
            acquired++;
        }
        assertTrue("5th concurrent request is rejected once the cap of 4 is reached",
                !limit.tryAcquire());
        limit.release();
        assertTrue("after one release, a new request can acquire the freed permit",
                limit.tryAcquire());

        System.out.println("ALL CHECKS PASSED");
    }
}
