package net.github.dctime.libs.routing;

import java.util.List;

/**
 * Orders an already-eligible candidate list -- never filters. {@link TranslationRouter} does the
 * hard filter (enabled, credentials present, not in cooldown/auth-error, rate+concurrency budget
 * plausible, vision-compatible, not already attempted this job) BEFORE calling {@link #rank};
 * every implementation only decides ORDER among candidates that have already passed that filter,
 * per the spec's explicit "先 hard filter，再 ranking" split. This keeps each implementation small
 * and independently testable (see tools/verify-routing-strategies).
 */
public interface RoutingStrategy {
    List<ProviderCandidate> rank(List<ProviderCandidate> eligible);
}
