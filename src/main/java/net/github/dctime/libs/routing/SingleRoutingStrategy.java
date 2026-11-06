package net.github.dctime.libs.routing;

import java.util.List;

/**
 * SINGLE mode's "ranking" is trivial by construction: {@link TranslationRouter} already forces the
 * eligible pool down to at most one candidate (whichever provider {@code Config.ENDPOINT_CONFIG}
 * names, or empty if that one isn't itself eligible) before any {@link RoutingStrategy} is ever
 * consulted for SINGLE mode -- there is nothing to rank, and no fallback consideration happens at
 * all (matches the spec's explicit "Single 模式完全保留舊行為，失敗不 fallback"). This class exists
 * only so every {@link ProviderMode} maps to a strategy instance uniformly; it is never expected to
 * receive more than one candidate.
 */
public final class SingleRoutingStrategy implements RoutingStrategy {
    @Override
    public List<ProviderCandidate> rank(List<ProviderCandidate> eligible) {
        return eligible;
    }
}
