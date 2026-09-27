package net.github.dctime.libs.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rotates the starting point through {@code eligible} on every call, using a single, monotonically
 * increasing {@link AtomicInteger} shared across all calls to THIS instance (a singleton, held by
 * {@code TranslationRouter} -- a fresh instance per job would rotate nothing). Thread-safe via the
 * atomic; never random, per the spec's explicit determinism requirement.
 * <p>
 * A temporarily-ineligible provider (cooldown, disabled, no budget) is simply absent from {@code
 * eligible} for that call -- it's skipped for THIS rotation without consuming a cursor slot
 * permanently reserved for it, and it naturally rejoins rotation the next time it's eligible again
 * (see tools/verify-routing-strategies for the "skip one, don't starve it" case). Because the
 * cursor keeps advancing regardless of how large {@code eligible} is on any given call, a provider
 * that was excluded for a while doesn't get an unfair head start OR an unfair penalty once it
 * returns -- it just resumes taking its turn in whatever the current eligible set's rotation is.
 */
public final class RoundRobinRoutingStrategy implements RoutingStrategy {

    private final AtomicInteger cursor = new AtomicInteger(0);

    @Override
    public List<ProviderCandidate> rank(List<ProviderCandidate> eligible) {
        if (eligible.isEmpty()) return List.of();

        int size = eligible.size();
        int start = Math.floorMod(cursor.getAndIncrement(), size);
        List<ProviderCandidate> rotated = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rotated.add(eligible.get((start + i) % size));
        }
        return rotated;
    }
}
