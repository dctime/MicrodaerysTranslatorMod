package net.github.dctime.libs.routing;

/**
 * How {@link TranslationRouter} picks a provider for each translation. Persisted in
 * {@code Config.PROVIDER_MODE} by name -- append-only if new modes are ever added, same reasoning
 * as {@code Config.EndPoint}.
 */
public enum ProviderMode {
    /** Exactly today's pre-router behavior: always {@code Config.ENDPOINT_CONFIG}, no fallback at
     *  all if it fails -- the player picked ONE provider on purpose (debugging/benchmarking/
     *  comparing translations), so silently trying another would be more confusing, not less. */
    SINGLE,
    /** Fixed order (lowest {@code priority} number first) among enabled+eligible providers, with
     *  fallback to the next one on a transient failure. */
    PRIORITY,
    /** Rotates through enabled+eligible providers in a stable order, skipping (not stalling on) a
     *  temporarily-ineligible one. */
    ROUND_ROBIN,
    /** Deterministic score (rate usage / in-flight load / latency / recent failures / priority
     *  bias) among enabled+eligible providers, lowest score first. Recommended default. */
    AUTOMATIC
}
