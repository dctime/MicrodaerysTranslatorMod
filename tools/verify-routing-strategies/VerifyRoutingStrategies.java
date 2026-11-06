// Regression check for net.github.dctime.libs.routing.{PriorityRoutingStrategy,
// RoundRobinRoutingStrategy, AutomaticRoutingStrategy} -- exercises the REAL production ordering
// logic against hand-built ProviderCandidate instances (real ProviderRuntimeState objects, driven
// through their real public API -- incrementInFlight/tryAcquireRate/recordSuccess/
// recordTransientFailure -- not a reimplementation).
//
// Confirmed headless-loadable without the NeoForge/Minecraft classpath: ProviderCandidate/
// ProviderRuntimeState/the four RoutingStrategy implementations only ever reference Config.EndPoint
// (a nested enum with no static dependency on Config's own ModConfigSpec-building <clinit> -- see
// tools/verify-provider-adapters' own header comment for the prior confirmation of this) -- never
// Config's actual TOML-backed fields. This file's own Run instructions below deliberately omit any
// NeoForge/Minecraft jar, and it still runs.
//
// Not covered here: TranslationRouter's hard filter (enabled/credentials/cooldown/vision) -- these
// strategies only ORDER an already-eligible list, they never filter (see RoutingStrategy's own
// javadoc), so this file never constructs a candidate expected to be filtered out; ProviderPool's
// real Config-reading path (needs the real NeoForge classpath, same limitation
// verify-provider-adapters already discloses for ProviderConfigResolver.resolve()); and the U1
// rate-limiter-staleness regression specifically (AutomaticRoutingStrategy.rank() hardcodes
// System.currentTimeMillis() internally with no injectable clock, so re-deriving "an idle
// provider's stale rate count evicts correctly over real elapsed time" through this layer would
// need an actual wall-clock sleep -- that regression is instead covered precisely, without sleeping,
// at the RateLimiter level in tools/verify-rate-limiter).
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-routing-strategies tools/verify-routing-strategies/VerifyRoutingStrategies.java
//   java -cp "tools/verify-routing-strategies:$MAIN_CLASSES" VerifyRoutingStrategies

import net.github.dctime.Config;
import net.github.dctime.libs.provider.ProviderSettings;
import net.github.dctime.libs.routing.*;

import java.util.List;

public class VerifyRoutingStrategies {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    /** adapter is deliberately null -- none of the three RoutingStrategy implementations ever
     *  dereference ProviderCandidate.adapter(), they only read priority/maxRequestsPerMinute/
     *  supportsVision/runtimeState. */
    private static ProviderCandidate candidate(Config.EndPoint endpoint, int priority, int rpmCap) {
        ProviderRuntimeState state = new ProviderRuntimeState(endpoint);
        ProviderSettings settings = ProviderSettings.of(endpoint, "key", "model");
        return new ProviderCandidate(endpoint, null, settings, state, true, priority, rpmCap, true);
    }

    public static void main(String[] args) {
        // ============================ PriorityRoutingStrategy ============================
        PriorityRoutingStrategy priorityStrategy = new PriorityRoutingStrategy();

        List<ProviderCandidate> unsorted = List.of(
                candidate(Config.EndPoint.GROQ, 3, 30),
                candidate(Config.EndPoint.GOOGLE_AI_STUDIO, 1, 10),
                candidate(Config.EndPoint.NVIDIA_NIM, 2, 30)
        );
        List<ProviderCandidate> sorted = priorityStrategy.rank(unsorted);
        assertTrue("priority 1 (Google) is ranked first", sorted.get(0).endpoint() == Config.EndPoint.GOOGLE_AI_STUDIO);
        assertTrue("priority 2 (NVIDIA) is ranked second", sorted.get(1).endpoint() == Config.EndPoint.NVIDIA_NIM);
        assertTrue("priority 3 (Groq) is ranked third", sorted.get(2).endpoint() == Config.EndPoint.GROQ);

        List<ProviderCandidate> tied = List.of(
                candidate(Config.EndPoint.GOOGLE_AI_STUDIO, 1, 10),
                candidate(Config.EndPoint.GROQ, 1, 30)
        );
        List<ProviderCandidate> tiedSorted = priorityStrategy.rank(tied);
        assertTrue("equal priority keeps the original (input list) relative order -- Google before Groq, "
                        + "a deterministic stable-sort tie-break, never random",
                tiedSorted.get(0).endpoint() == Config.EndPoint.GOOGLE_AI_STUDIO
                        && tiedSorted.get(1).endpoint() == Config.EndPoint.GROQ);

        // ============================ RoundRobinRoutingStrategy ============================
        RoundRobinRoutingStrategy roundRobin = new RoundRobinRoutingStrategy();
        List<ProviderCandidate> threePool = List.of(
                candidate(Config.EndPoint.GOOGLE_AI_STUDIO, 1, 10),
                candidate(Config.EndPoint.NVIDIA_NIM, 2, 30),
                candidate(Config.EndPoint.GROQ, 3, 30)
        );

        Config.EndPoint leader1 = roundRobin.rank(threePool).get(0).endpoint();
        Config.EndPoint leader2 = roundRobin.rank(threePool).get(0).endpoint();
        Config.EndPoint leader3 = roundRobin.rank(threePool).get(0).endpoint();
        Config.EndPoint leader4 = roundRobin.rank(threePool).get(0).endpoint();
        assertTrue("round robin rotates G -> N -> Groq -> G across 4 consecutive calls with the same full pool "
                        + "(acceptance test 9): got " + leader1 + " -> " + leader2 + " -> " + leader3 + " -> " + leader4,
                leader1 == Config.EndPoint.GOOGLE_AI_STUDIO && leader2 == Config.EndPoint.NVIDIA_NIM
                        && leader3 == Config.EndPoint.GROQ && leader4 == Config.EndPoint.GOOGLE_AI_STUDIO);

        // Non-starvation: exclude NVIDIA from the eligible list for one call (simulating a cooldown),
        // then keep including the full pool again -- NVIDIA must become leader again within a bounded
        // number of subsequent calls, not be perpetually skipped (acceptance test 10).
        RoundRobinRoutingStrategy skipStrategy = new RoundRobinRoutingStrategy();
        List<ProviderCandidate> withoutNvidia = List.of(
                candidate(Config.EndPoint.GOOGLE_AI_STUDIO, 1, 10),
                candidate(Config.EndPoint.GROQ, 3, 30)
        );
        skipStrategy.rank(withoutNvidia); // NVIDIA "in cooldown" this round -- simply absent from the list
        boolean nvidiaBecameLeaderAgain = false;
        for (int i = 0; i < threePool.size() * 2; i++) {
            if (skipStrategy.rank(threePool).get(0).endpoint() == Config.EndPoint.NVIDIA_NIM) {
                nvidiaBecameLeaderAgain = true;
                break;
            }
        }
        assertTrue("a provider skipped for one round (simulating cooldown) becomes leader again within "
                        + "a bounded number of subsequent calls once it's eligible again -- not starved",
                nvidiaBecameLeaderAgain);

        RoundRobinRoutingStrategy emptyPoolStrategy = new RoundRobinRoutingStrategy();
        assertTrue("an empty eligible list returns an empty list, not a crash",
                emptyPoolStrategy.rank(List.of()).isEmpty());

        // ============================ AutomaticRoutingStrategy ============================
        AutomaticRoutingStrategy automatic = new AutomaticRoutingStrategy();

        ProviderCandidate idleTopPriority = candidate(Config.EndPoint.GOOGLE_AI_STUDIO, 1, 10);
        ProviderCandidate busyLowerPriority = candidate(Config.EndPoint.GROQ, 5, 30);
        // make Groq visibly busy: 2 in-flight (== DEFAULT_PROVIDER_CONCURRENCY, fully saturated) and
        // two consecutive failures -- Google stays completely idle/healthy.
        busyLowerPriority.runtimeState().incrementInFlight();
        busyLowerPriority.runtimeState().incrementInFlight();
        busyLowerPriority.runtimeState().recordTransientFailure(ProviderFailureType.SERVER, null);
        busyLowerPriority.runtimeState().recordTransientFailure(ProviderFailureType.SERVER, null);

        List<ProviderCandidate> autoRanked = automatic.rank(List.of(busyLowerPriority, idleTopPriority));
        assertTrue("AUTOMATIC picks the idle top-priority provider over a busy, failing lower-priority one "
                        + "even though it appeared SECOND in the input list",
                autoRanked.get(0).endpoint() == Config.EndPoint.GOOGLE_AI_STUDIO);

        // Priority is a bias, not an override: an idle LOWER-priority provider can still out-rank a
        // busy HIGHER-priority one (spec's own explicit AUTOMATIC-mode example).
        ProviderCandidate idleLowPriority = candidate(Config.EndPoint.OPENROUTER, 9, 20);
        ProviderCandidate busyTopPriority = candidate(Config.EndPoint.GOOGLE_AI_STUDIO, 1, 10);
        for (int i = 0; i < 2; i++) busyTopPriority.runtimeState().incrementInFlight();
        busyTopPriority.runtimeState().recordTransientFailure(ProviderFailureType.SERVER, null);
        busyTopPriority.runtimeState().recordTransientFailure(ProviderFailureType.SERVER, null);
        busyTopPriority.runtimeState().recordTransientFailure(ProviderFailureType.SERVER, null);

        List<ProviderCandidate> biasRanked = automatic.rank(List.of(busyTopPriority, idleLowPriority));
        assertTrue("a busy priority-1 provider can be out-ranked by an idle priority-9 provider "
                        + "-- priority alone never overrides real-time load in AUTOMATIC mode",
                biasRanked.get(0).endpoint() == Config.EndPoint.OPENROUTER);

        List<ProviderCandidate> emptyAutoRanked = automatic.rank(List.of());
        assertTrue("an empty eligible list returns an empty list, not a crash", emptyAutoRanked.isEmpty());

        System.out.println("ALL CHECKS PASSED");
    }
}
