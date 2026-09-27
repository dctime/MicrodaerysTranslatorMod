package net.github.dctime.libs.routing;

/**
 * Pure scoring function for {@code AutomaticRoutingStrategy} -- no Minecraft/I-O dependency, so
 * this is directly unit-testable (see tools/verify-provider-scorer). LOWER score is better.
 * Deliberately a simple weighted sum of normalized terms, not a learned/ML model, per the spec's
 * explicit "不要做過度複雜 AI scoring" -- every term is a named, commented, tunable constant, not a
 * magic number, so this stays debuggable by reading it.
 * <p>
 * All inputs are already-computed primitives (not {@link ProviderRuntimeState} itself), so this
 * class never has to reason about thread-safety, locking, or the wall clock -- the caller
 * ({@code AutomaticRoutingStrategy}) is responsible for reading those atomically/consistently
 * before calling in.
 * <p>
 * <b>Cold-start / stale-reputation handling</b> (added after the first real in-game playtest of
 * AUTOMATIC mode surfaced both edge cases live): the latency term has two special-cased branches,
 * mutually exclusive, never both applied to the same candidate:
 * <ol>
 *     <li>A provider with NO latency sample yet ({@link ProviderRuntimeState#hasLatencySample()}
 *         false) gets a flat {@link #NEUTRAL_UNTRIED_PENALTY}, not 0. Reading {@link
 *         ProviderRuntimeState#averageLatencyMs()} directly for an untried provider returns 0.0
 *         (its unset default), which used to make every never-tried provider look BETTER than any
 *         provider with real, known-good latency data -- so a job would keep "spending" its first
 *         real attempt exploring every enabled provider once, even ones a session had already
 *         proven were fine, before AUTOMATIC's own scoring could reflect reality. A flat neutral
 *         penalty still lets an untried provider be picked in reasonable rotation -- it just no
 *         longer looks artificially perfect purely for being unknown.</li>
 *     <li>A provider WITH a latency sample gets its penalty DISCOUNTED the longer it's been since
 *         it was last actually attempted ({@link ProviderRuntimeState#lastAttemptMillis()}), up to
 *         {@link #STALENESS_MAX_DISCOUNT}. Without this, one old bad data point (a provider that
 *         was slow or failing minutes ago, possibly transiently) keeps outscoring it forever in a
 *         session, since nothing else ever updates that average once AUTOMATIC stops picking it --
 *         a self-reinforcing exile with the same shape as the U1 rate-limiter staleness bug this
 *         file's sibling classes already fixed once. The discount is capped, not full forgiveness:
 *         a genuinely bad provider doesn't get treated as brand-new just for sitting idle, it's
 *         periodically given a fairer-but-still-penalized chance to prove it's improved. {@link
 *         #latencyPenalty} STRUCTURALLY floors the decayed result at {@link
 *         #NEUTRAL_UNTRIED_PENALTY} whenever the raw (undiscounted) penalty exceeds it (round 038
 *         point AC1 -- tuning {@link #STALENESS_MAX_DISCOUNT} alone only guaranteed this at the
 *         single worst-case input, not for every provider that's worse than neutral; this mod's own
 *         measured OpenRouter fell inside the gap that left open) -- real, if aging, negative
 *         evidence must never be worth less than having no evidence at all, for ANY provider, not
 *         just the slowest one measured so far.</li>
 *     <li>Both branches' constants are calibrated against real measured latency, not guessed --
 *         see {@link #LATENCY_FULL_PENALTY_MS}'s own javadoc: a 5s clamp made a 6s provider and a
 *         15s provider score identically, discarding exactly the distinction this scorer exists to
 *         make.</li>
 *     <li>"{@code priority is a nudge, not an override}" ({@link #PRIORITY_BIAS_PER_RANK}) is true
 *         relative to failure/rate-limit/severe-latency penalties, but priority's own range (0 to
 *         0.5 across priorities 1-11) is HALF the latency penalty's range (0 to 1) -- among a group
 *         of candidates that are all otherwise healthy (low failure count, low latency penalty),
 *         priority ends up being the DOMINANT term, not a minor tiebreaker. That's arguably the
 *         right behavior (priority should matter most when everything else is roughly equal), but
 *         it means the "nudge" framing only holds against the more severe penalties, not
 *         universally.</li>
 *     <li>{@link #STALENESS_FULL_RECOVERY_MS} directly controls how OFTEN a poorly-performing
 *         provider gets re-explored -- and in this mod, every re-exploration is a real, player-
 *         visible slow translation (there's no way to probe a provider's health without actually
 *         routing a real job to it). Roughly: every ~5 minutes, an underperforming enabled provider
 *         gets tried again, and that one translation will be slow.</li>
 * </ol>
 */
public final class ProviderScorer {

    // Tunable constants, each documented with what it represents -- not claimed to be "correct" in
    // any rigorous sense, just a reasonable, debuggable starting point (spec's own framing: a
    // deterministic score, not a black box).
    //
    // LATENCY_FULL_PENALTY_MS = 15000 (not the original 5000): mailbox review round 037 point Q1 --
    // the actual measured latency range across this mod's 11 providers, from a real playtest, is
    // roughly 0.3s (Groq) to 15s (NVIDIA NIM), a 50x spread. A 5s clamp made every provider at or
    // beyond 5s score IDENTICALLY (a 6s OpenRouter response and a 15s NVIDIA response both hit the
    // 1.0 ceiling) -- exactly the two providers this scorer most needs to be able to tell apart.
    // 15000 covers the worst observed case while still meaningfully penalizing anything close to it.
    private static final double LATENCY_FULL_PENALTY_MS = 15_000.0;
    private static final double FAILURE_PENALTY_PER_STRIKE = 0.2; // each consecutive failure adds this much, capped at 1.0
    // priority is a nudge relative to failure/rate-limit/severe-latency penalties, but its own range
    // (0 to 0.5 across priorities 1-11) is HALF the latency penalty's range (0 to 1) -- among a group
    // of otherwise-healthy providers (all scoring in the low end of the latency range), priority ends
    // up dominating the ranking, not merely nudging it. See class javadoc, point 3 (round 037).
    private static final double PRIORITY_BIAS_PER_RANK = 0.05;

    /** The INTENT: an untried provider is assumed about as fast as a decent-but-not-great provider
     *  -- worse than Groq, roughly like Google, better than OpenRouter/NVIDIA (this mod's own
     *  measured range). Written as a millisecond value, not a bare 0..1 penalty, specifically so it
     *  stays meaningful if {@link #LATENCY_FULL_PENALTY_MS} is ever retuned again (mailbox review
     *  round 038, point AC2): {@link #NEUTRAL_UNTRIED_PENALTY} used to be a bare "0.3", which stayed
     *  numerically 0.3 when {@code LATENCY_FULL_PENALTY_MS} moved from 5000 to 15000 -- but its
     *  MEANING silently drifted from "1.5s-equivalent" to "4.5s-equivalent" purely as a side effect
     *  of the other constant changing, not a reconsidered decision. Deriving the penalty from this
     *  fixed millisecond intent instead means the intent survives future clamp retuning automatically. */
    private static final double NEUTRAL_UNTRIED_EQUIVALENT_MS = 1500.0;

    /** Derived, not independently chosen -- see {@link #NEUTRAL_UNTRIED_EQUIVALENT_MS}. */
    private static final double NEUTRAL_UNTRIED_PENALTY = NEUTRAL_UNTRIED_EQUIVALENT_MS / LATENCY_FULL_PENALTY_MS;

    /** How long since a provider's last attempt before its latency penalty is discounted by the
     *  full {@link #STALENESS_MAX_DISCOUNT} -- see class javadoc, point 2. 5 minutes: long enough
     *  that a provider actively being used every session never sees any discount (staleness stays
     *  near 0), short enough that a provider AUTOMATIC stopped picking gets re-considered within
     *  the same play session rather than only across restarts (runtime state doesn't survive those
     *  anyway). This value directly controls how OFTEN a poorly-performing provider gets re-explored
     *  -- and each re-exploration is a player-visible slow translation (see class javadoc, point 4);
     *  disclosed as such in the completion report, not just here.
     *  <p>
     *  Deliberately re-checked against {@link #NEUTRAL_UNTRIED_PENALTY} (round 037 point Q1's other
     *  finding): {@link #STALENESS_MAX_DISCOUNT} is chosen so a fully-discounted worst-case provider
     *  (rawPenalty 1.0) never scores as well as (or better than) a never-tried one -- see that
     *  constant's own javadoc for why. */
    private static final long STALENESS_FULL_RECOVERY_MS = 5 * 60_000L;

    /** The most a stale bad reputation can be discounted, as a fraction. Mailbox review round 037,
     *  point Q1 first tried fixing this by lowering the fraction (0.7 -> 0.5) so the WORST-CASE
     *  provider (rawPenalty 1.0), even fully discounted, would floor above {@link
     *  #NEUTRAL_UNTRIED_PENALTY} -- but round 038 point AC1 found that only holds AT the worst case:
     *  {@code rawPenalty * (1 - STALENESS_MAX_DISCOUNT)} is a straight multiply, so any {@code
     *  rawPenalty} between {@code NEUTRAL_UNTRIED_PENALTY} and {@code NEUTRAL_UNTRIED_PENALTY /
     *  (1 - STALENESS_MAX_DISCOUNT)} still decays BELOW the neutral floor -- concretely, this mod's
     *  own measured OpenRouter (6s, rawPenalty 0.4 at the current clamp) decayed to 0.2, which
     *  WOULD have ranked a known-6-second provider ahead of a never-tried one after 5 minutes idle.
     *  The real fix is structural, not arithmetic (see {@link #latencyPenalty}): the floor is now an
     *  explicit {@code Math.max} against {@link #NEUTRAL_UNTRIED_PENALTY}, applied whenever {@code
     *  rawPenalty} exceeds it, so the invariant holds for every input, not just the one this
     *  constant happens to have been tuned against -- future edits to this constant or to {@link
     *  #LATENCY_FULL_PENALTY_MS} can't silently reopen the gap the way lowering 0.7 to 0.5 alone
     *  did. This constant still controls how MUCH of a discount a stale bad provider gets before
     *  hitting that floor -- 0.5 means it can still legitimately score BETTER than neutral once its
     *  raw penalty itself is already below neutral (e.g. a provider slightly slower than Google but
     *  still faster than the neutral assumption) -- it just can no longer cross BELOW neutral while
     *  its un-discounted penalty was above it. */
    private static final double STALENESS_MAX_DISCOUNT = 0.5;

    private ProviderScorer() {
    }

    /**
     * @param rateUsage        current requests-in-window / that provider's own configured RPM cap,
     *                         from {@link ProviderRuntimeState#currentRateUsage} (NOT {@link
     *                         ProviderRuntimeState#currentRateCount}, which can be stale for an idle
     *                         provider -- see that method's javadoc, mailbox review round 026 point
     *                         U1). Expected roughly 0..1, but not clamped here (a provider already
     *                         over its own cap should score even worse, not get artificially capped
     *                         to look no worse than one exactly at the cap).
     * @param inFlightCount    {@link ProviderRuntimeState#inFlight()}.
     * @param providerConcurrency this provider's own concurrency limit (see {@link
     *                         ProviderRuntimeState#DEFAULT_PROVIDER_CONCURRENCY}).
     * @param averageLatencyMs {@link ProviderRuntimeState#averageLatencyMs()} -- ignored (see {@code
     *                         hasLatencySample}) when there's no real sample yet.
     * @param hasLatencySample {@link ProviderRuntimeState#hasLatencySample()} -- selects which of
     *                         the two latency-penalty branches applies (class javadoc).
     * @param millisSinceLastAttempt {@link ProviderRuntimeState#lastAttemptMillis()}, already
     *                         converted to "how long ago" by the caller (i.e. {@code now -
     *                         lastAttemptMillis()}) -- only read when {@code hasLatencySample} is
     *                         true; a genuinely untried provider's value here is ignored, not just
     *                         zero (see class javadoc, point 1 vs. point 2 not overlapping).
     * @param consecutiveFailures {@link ProviderRuntimeState#consecutiveFailures()}.
     * @param priority         1 = highest priority (see {@link ProviderCandidate#priority()}).
     */
    public static double score(double rateUsage, int inFlightCount, int providerConcurrency,
                                double averageLatencyMs, boolean hasLatencySample, long millisSinceLastAttempt,
                                int consecutiveFailures, int priority) {
        double inFlightRatio = providerConcurrency <= 0 ? 0.0 : inFlightCount / (double) providerConcurrency;
        double latencyPenalty = latencyPenalty(averageLatencyMs, hasLatencySample, millisSinceLastAttempt);
        double failurePenalty = clamp01(consecutiveFailures * FAILURE_PENALTY_PER_STRIKE);
        double priorityBias = Math.max(0, priority - 1) * PRIORITY_BIAS_PER_RANK;

        return rateUsage + inFlightRatio + latencyPenalty + failurePenalty + priorityBias;
    }

    /** Structural guarantee (mailbox review round 038, point AC1), not an arithmetic coincidence:
     *  a provider with real evidence that it's WORSE than the neutral assumption ({@code rawPenalty
     *  > NEUTRAL_UNTRIED_PENALTY}) can never decay, however stale, to a score AT OR BELOW an untried
     *  provider's flat penalty -- {@code Math.max} enforces the floor directly instead of relying on
     *  {@link #STALENESS_MAX_DISCOUNT} having been tuned just right for whatever {@code rawPenalty}
     *  happens to occur. A provider whose raw penalty is already BELOW neutral (genuinely faster
     *  than the neutral assumption) is untouched by the floor -- {@code rawPenalty <=
     *  NEUTRAL_UNTRIED_PENALTY} skips straight to the plain decayed value, since there's nothing to
     *  protect it from (it was never going to look "too good" for having real data). */
    private static double latencyPenalty(double averageLatencyMs, boolean hasLatencySample, long millisSinceLastAttempt) {
        if (!hasLatencySample) {
            return NEUTRAL_UNTRIED_PENALTY;
        }
        double rawPenalty = clamp01(averageLatencyMs / LATENCY_FULL_PENALTY_MS);
        double staleness = clamp01(millisSinceLastAttempt / (double) STALENESS_FULL_RECOVERY_MS);
        double decayed = rawPenalty * (1 - staleness * STALENESS_MAX_DISCOUNT);
        return rawPenalty > NEUTRAL_UNTRIED_PENALTY ? Math.max(NEUTRAL_UNTRIED_PENALTY, decayed) : decayed;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
