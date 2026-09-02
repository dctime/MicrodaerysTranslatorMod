package net.github.dctime.libs.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts by {@link ProviderCandidate#priority()} ascending (1 = highest, tried first). {@link
 * List#sort} is a stable sort, so two candidates with equal priority keep their original relative
 * order -- which is {@code ProviderInfo.ALL}'s fixed order, since that's the order {@code
 * ProviderPool} builds candidates in -- rather than an unspecified or random tie-break.
 */
public final class PriorityRoutingStrategy implements RoutingStrategy {
    @Override
    public List<ProviderCandidate> rank(List<ProviderCandidate> eligible) {
        List<ProviderCandidate> sorted = new ArrayList<>(eligible);
        sorted.sort(Comparator.comparingInt(ProviderCandidate::priority));
        return sorted;
    }
}
