package net.github.dctime.libs.routing;

import net.github.dctime.Config;

import java.util.EnumMap;
import java.util.Map;

/**
 * One long-lived {@link ProviderRuntimeState} per {@link Config.EndPoint}, built once at class-init
 * and never rebuilt for the life of the game process -- same lifetime pattern as the old {@code
 * Translator.CONCURRENCY_LIMIT}/{@code REQUEST_RATE_LIMITER} statics this replaces (mailbox review
 * round 023/024, point R1). Deliberately never touches {@code Config} or TOML -- purely runtime,
 * exactly like {@link ProviderRuntimeState} itself.
 */
public final class ProviderRuntimeRegistry {

    private static final Map<Config.EndPoint, ProviderRuntimeState> STATES = build();

    private ProviderRuntimeRegistry() {
    }

    private static Map<Config.EndPoint, ProviderRuntimeState> build() {
        Map<Config.EndPoint, ProviderRuntimeState> map = new EnumMap<>(Config.EndPoint.class);
        for (Config.EndPoint endpoint : Config.EndPoint.values()) {
            map.put(endpoint, new ProviderRuntimeState(endpoint));
        }
        return map;
    }

    public static ProviderRuntimeState forEndpoint(Config.EndPoint endpoint) {
        ProviderRuntimeState state = STATES.get(endpoint);
        if (state == null) {
            // Unreachable: build() is exhaustive over Config.EndPoint.values() by construction.
            throw new IllegalStateException("No ProviderRuntimeState registered for " + endpoint);
        }
        return state;
    }
}
