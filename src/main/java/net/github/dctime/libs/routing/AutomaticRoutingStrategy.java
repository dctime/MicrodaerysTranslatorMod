package net.github.dctime.libs.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts by {@link ProviderScorer#score} ascending (lower is better). Reads each candidate's runtime
 * numbers fresh at rank time -- {@link ProviderRuntimeState#currentRateUsage} (NOT {@link
 * ProviderRuntimeState#currentRateCount}, which is stale for an idle provider -- see that method's
 * javadoc), in-flight count, average latency, consecutive failures -- and combines them with the
 * candidate's priority as a bias, per {@link ProviderScorer}. Tie-break is the same stable-sort
 * property {@link PriorityRoutingStrategy} relies on: equal scores keep {@code eligible}'s original
 * relative order ({@code ProviderInfo.ALL}'s fixed order), never random.
 */
public final class AutomaticRoutingStrategy implements RoutingStrategy {

    @Override
    public List<ProviderCandidate> rank(List<ProviderCandidate> eligible) {
        long now = System.currentTimeMillis();
        List<ProviderCandidate> sorted = new ArrayList<>(eligible);
        sorted.sort(Comparator.comparingDouble(c -> scoreOf(c, now)));
        return sorted;
    }

    private double scoreOf(ProviderCandidate candidate, long nowMillis) {
        ProviderRuntimeState state = candidate.runtimeState();
        int rpmCap = candidate.maxRequestsPerMinute();
        double rateUsage = rpmCap <= 0 ? 0.0 : state.currentRateUsage(nowMillis) / (double) rpmCap;
        long millisSinceLastAttempt = nowMillis - state.lastAttemptMillis();

        return ProviderScorer.score(rateUsage, state.inFlight(), ProviderRuntimeState.DEFAULT_PROVIDER_CONCURRENCY,
                state.averageLatencyMs(), state.hasLatencySample(), millisSinceLastAttempt,
                state.consecutiveFailures(), candidate.priority());
    }
}
