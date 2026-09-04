// Regression check for net.github.dctime.libs.routing.ProviderScorer -- the pure weighted-sum
// scoring function AutomaticRoutingStrategy sorts candidates by (LOWER score = better/picked
// first). No Minecraft dependency, every input is a plain primitive, so this calls the REAL
// production class directly with hand-picked numbers -- no copy, no headless-limitation caveat.
//
// What this does NOT verify: that AutomaticRoutingStrategy actually reads the right live numbers
// off a real ProviderRuntimeState/ProviderCandidate and feeds them into this function correctly --
// see tools/verify-routing-strategies for that (it constructs real ProviderRuntimeState instances
// and exercises the real AutomaticRoutingStrategy.rank()). This file only checks the scoring
// FORMULA itself in isolation.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-provider-scorer tools/verify-provider-scorer/VerifyProviderScorer.java
//   java -cp "tools/verify-provider-scorer:$MAIN_CLASSES" VerifyProviderScorer

import net.github.dctime.libs.routing.ProviderScorer;

public class VerifyProviderScorer {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- a fully idle, healthy (real, measured, near-zero latency), top-priority provider
        // scores at (or very near) zero. hasLatencySample=true, millisSinceLastAttempt=0 (just
        // attempted, no staleness discount) throughout this first block -- matches the ORIGINAL
        // pre-cold-start-redesign test semantics exactly (a "0.0 average latency" here means a real
        // measured 0ms, not "unknown"). ---
        double idle = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 1);
        assertTrue("a fully idle top-priority (1), proven-fast provider scores 0.0", idle == 0.0);

        // --- higher rate usage strictly worsens the score, all else equal ---
        double lowRate = ProviderScorer.score(0.2, 0, 2, 0.0, true, 0L, 0, 1);
        double highRate = ProviderScorer.score(0.8, 0, 2, 0.0, true, 0L, 0, 1);
        assertTrue("higher rateUsage scores worse (higher) than lower rateUsage, all else equal",
                highRate > lowRate);

        // --- higher in-flight ratio strictly worsens the score ---
        double busy = ProviderScorer.score(0.0, 2, 2, 0.0, true, 0L, 0, 1); // 2/2 = fully saturated
        double free = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 1);
        assertTrue("a fully saturated provider (inFlight == concurrency limit) scores worse than an idle one",
                busy > free);

        // --- latency penalty is clamped at 1.0, not unbounded ---
        double veryLate = ProviderScorer.score(0.0, 0, 2, 999_999.0, true, 0L, 0, 1);
        double atCap = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, 0L, 0, 1);
        assertTrue("latency penalty is clamped -- a hugely-over-threshold latency scores the same as exactly at the cap",
                veryLate == atCap);

        // --- consecutive failures worsen the score, and are also clamped ---
        double oneFailure = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 1, 1);
        double manyFailures = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 50, 1);
        double fiveFailures = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 5, 1); // 5 * 0.2 = 1.0, already at the clamp
        assertTrue("one consecutive failure scores worse than zero", oneFailure > 0.0);
        assertTrue("failure penalty is clamped -- 50 consecutive failures scores the same as 5 (already at the clamp)",
                manyFailures == fiveFailures);

        // --- priority is a BIAS, not an override: a lower-priority (worse-ranked-on-paper)
        // provider that's otherwise idle can still out-score a higher-priority one that's busy ---
        double lowPriorityIdle = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 5); // priority 5, but idle
        double topPriorityBusy = ProviderScorer.score(0.9, 2, 2, 3000.0, true, 0L, 2, 1); // priority 1, but loaded
        assertTrue("a busy top-priority provider can score WORSE than an idle lower-priority one "
                        + "-- priority is a bias, not a hard override (spec's explicit AUTOMATIC-mode example)",
                topPriorityBusy > lowPriorityIdle);

        // --- priority bias itself is small and monotonic, never a cliff ---
        double p1 = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 1);
        double p2 = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 2);
        double p11 = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 11);
        assertTrue("priority 2 scores worse than priority 1, all else equal", p2 > p1);
        assertTrue("priority 11 scores worse than priority 2, all else equal", p11 > p2);
        assertTrue("the priority bias alone (idle, priority 11 vs priority 1) never exceeds 1.0 "
                        + "-- it's meant to be a small nudge, not able to single-handedly out-weigh a rate/failure penalty",
                (p11 - p1) < 1.0);

        // --- zero/negative provider concurrency never divides by zero or throws ---
        double zeroConcurrency = ProviderScorer.score(0.0, 0, 0, 0.0, true, 0L, 0, 1);
        assertTrue("a provider with concurrency limit 0 doesn't throw or produce NaN/Infinity",
                Double.isFinite(zeroConcurrency));

        // ============================================================================
        // Cold-start / stale-reputation handling (added after the first real in-game playtest of
        // AUTOMATIC mode -- see ProviderScorer's own class javadoc for the full rationale).
        // ============================================================================

        // --- an untried provider (hasLatencySample=false) is NOT treated as perfect (0 latency
        // penalty) just because averageLatencyMs defaults to 0.0 -- it gets a flat neutral penalty
        // instead, worse than a provider proven fast, better than one proven slow. The neutral
        // penalty is DERIVED from an explicit 1500ms intent (mailbox review round 038 point AC2),
        // not a bare constant -- at the current 15000ms clamp that's 0.1, not the 0.3 this file used
        // before that fix (0.3 was a numerically-unchanged but silently-redefined leftover from the
        // OLD 5000ms clamp, where 0.3 == 1500ms/5000ms; the intent was always 1500ms). ---
        double untried = ProviderScorer.score(0.0, 0, 2, 0.0, false, 0L, 0, 1);
        double provenFast = ProviderScorer.score(0.0, 0, 2, 0.0, true, 0L, 0, 1);
        double provenSlow = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, 0L, 0, 1);
        assertTrue("an untried provider (no latency sample) scores WORSE than one proven fast "
                        + "-- it no longer looks artificially perfect purely for being unknown",
                untried > provenFast);
        assertTrue("an untried provider still scores BETTER than one proven consistently slow "
                        + "-- it's a neutral guess, not a punishment",
                untried < provenSlow);
        assertTrue("the untried penalty is EXACTLY the same regardless of whatever stale "
                        + "averageLatencyMs/millisSinceLastAttempt values happen to be passed alongside it "
                        + "(both are documented as ignored when hasLatencySample is false)",
                untried == ProviderScorer.score(0.0, 0, 2, 999_999.0, false, 999_999_999L, 0, 1));

        // --- a provider that was slow a LONG time ago (stale bad reputation) scores better than
        // the SAME raw latency would score if it had JUST happened (fresh bad data), but never
        // fully clean -- the discount is capped, not full forgiveness. ---
        long fiveMinutesMs = 5 * 60_000L;
        double freshBadData = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, 0L, 0, 1); // just failed, at the penalty cap
        double staleBadData = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, fiveMinutesMs, 0, 1); // same lateness, 5 min ago
        assertTrue("a provider slow 5 minutes ago scores BETTER than the same slowness happening right now "
                        + "-- old bad data gets progressively discounted, not held against it forever",
                staleBadData < freshBadData);
        assertTrue("even fully stale (>= the recovery window), a provider that was at the WORST possible "
                        + "latency penalty (1.0) still isn't fully forgiven -- capped at a 50% discount, so its "
                        + "remaining penalty (0.5) is still noticeably worse than a provider proven genuinely fast (0.0)",
                staleBadData > provenFast);
        assertTrue("a fully-discounted, worst-case-latency provider never scores as well as (or better "
                        + "than) an untried provider",
                staleBadData >= untried);

        // Mailbox review round 038, point AC1's specific finding: the WORST-CASE check above passes
        // even without a structural fix, purely because 1.0 * (1 - 0.5) = 0.5 already happens to sit
        // above the (now smaller, 0.1) neutral value -- it does NOT exercise the actual bug. The real
        // gap is for a provider MEASURED as moderately worse than neutral (this mod's own Google
        // data, ~0.6-2.4s, overlaps the danger zone): raw penalty 0.15 (2250ms) decays to 0.15 * 0.5
        // = 0.075 at full staleness -- BELOW the 0.1 neutral floor -- unless the floor is a
        // structural Math.max, not just a hope that STALENESS_MAX_DISCOUNT was tuned generously
        // enough for whatever raw penalty happens to occur.
        double midRangeFresh = ProviderScorer.score(0.0, 0, 2, 2_250.0, true, 0L, 0, 1); // ~Google-range, just measured
        double midRangeStale = ProviderScorer.score(0.0, 0, 2, 2_250.0, true, fiveMinutesMs, 0, 1); // same, 5 min idle
        assertTrue("a moderately-slow-but-real measurement (2.25s, worse than neutral's 1.5s-equivalent) "
                        + "still decays over time, same as any other real data",
                midRangeStale < midRangeFresh);
        assertTrue("but a moderately-slow measurement's fully-stale score NEVER drops below the neutral "
                        + "untried penalty -- without the structural floor this would be 0.075 < untried's 0.1, "
                        + "meaning a provider we MEASURED as decent-but-not-great would end up looking WORSE "
                        + "for having been idle than a provider we've never even tried",
                midRangeStale >= untried);

        // --- staleness clamps at the recovery window -- waiting even longer never discounts MORE
        // than the cap allows. ---
        double atRecoveryWindow = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, fiveMinutesMs, 0, 1);
        double wayPastRecoveryWindow = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, fiveMinutesMs * 100, 0, 1);
        assertTrue("staleness discount is clamped at the recovery window -- waiting 100x longer than the "
                        + "recovery window doesn't discount the penalty any further than waiting exactly the window",
                atRecoveryWindow == wayPastRecoveryWindow);

        // --- a provider attempted very recently (small millisSinceLastAttempt) gets little to no
        // discount -- staleness recovery is about providers that have gone UNUSED for a while, not
        // ones being actively (and still badly) used. ---
        double justFailedNow = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, 0L, 0, 1);
        double failedOneSecondAgo = ProviderScorer.score(0.0, 0, 2, 15_000.0, true, 1_000L, 0, 1);
        assertTrue("a provider that failed 1 second ago scores the SAME (no meaningful discount yet) as one "
                        + "that just failed this instant -- staleness only matters once real time has passed",
                Math.abs(justFailedNow - failedOneSecondAgo) < 0.01);

        // --- the floor never touches a provider whose raw penalty is already BELOW neutral (a
        // genuinely fast provider shouldn't get artificially INFLATED up to the neutral value just
        // for being idle for a while) ---
        double fastAndFresh = ProviderScorer.score(0.0, 0, 2, 800.0, true, 0L, 0, 1); // Groq-ish, ~53ms-equivalent raw
        double fastAndStale = ProviderScorer.score(0.0, 0, 2, 800.0, true, fiveMinutesMs, 0, 1); // same, 5 min idle
        assertTrue("a provider whose raw penalty is already BELOW neutral keeps decaying normally toward "
                        + "an even lower score when idle -- the floor only protects providers ABOVE neutral, "
                        + "it never pulls an already-good score UP",
                fastAndStale <= fastAndFresh);
        assertTrue("specifically: a proven-fast, long-idle provider can legitimately score BETTER than "
                        + "neutral -- the floor does not apply to it at all",
                fastAndStale < untried);

        System.out.println("ALL CHECKS PASSED");
    }
}
