package net.github.dctime.libs.routing;

import net.github.dctime.Config;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderAdapterRegistry;
import net.github.dctime.libs.provider.ProviderConfigResolver;
import net.github.dctime.libs.provider.ProviderInfo;
import net.github.dctime.libs.provider.ProviderSettings;
import net.github.dctime.libs.provider.TranslationProviderAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for "what providers exist, with what current settings and runtime state"
 * -- {@code Translator}, {@code TranslationRouter}, the Manage Providers/Provider Detail GUI, and
 * {@code TranslationConnectionTester} all read through this instead of each independently
 * re-resolving {@code Config} + {@link ProviderConfigResolver} + {@link ProviderAdapterRegistry} +
 * {@link ProviderRuntimeRegistry}, which is exactly the kind of three-independent-copies problem
 * {@link ProviderAdapterRegistry} already fixed for adapters alone in the previous round -- this
 * applies the same fix one layer up, at the settings+runtime-state layer.
 * <p>
 * Never caches the built list -- {@link #all()} re-resolves from {@code Config} on every call, so
 * an edit saved via Manage Providers (a plain {@code Config} write, see {@code
 * PendingTranslatorConfig.saveToConfig()}) is visible on the very next translation job with no
 * extra invalidation step needed. {@link ProviderCandidate#runtimeState()} is the one field NOT
 * re-resolved -- it's the same long-lived {@link ProviderRuntimeState} instance every time (see
 * {@link ProviderRuntimeRegistry}), which is what lets in-flight/cooldown/failure counts persist
 * across the many {@link #all()} calls a single game session makes.
 */
public final class ProviderPool {

    private ProviderPool() {
    }

    /** Every provider, in {@link ProviderInfo#ALL}'s fixed order -- the "stable full order" every
     *  {@link RoutingStrategy} tie-break and {@link RoundRobinRoutingStrategy} rotation relies on.
     *  Does NOT filter by enabled/credentials/cooldown -- that's the caller's job ({@code
     *  TranslationRouter} for routing eligibility, the GUI for display). */
    public static List<ProviderCandidate> all() {
        List<ProviderCandidate> candidates = new ArrayList<>(ProviderInfo.ALL.size());
        for (ProviderInfo info : ProviderInfo.ALL) {
            candidates.add(buildCandidate(info.endpoint()));
        }
        return candidates;
    }

    public static ProviderCandidate buildCandidate(Config.EndPoint endpoint) {
        ProviderConfigResolver.ResolvedProviderConfig resolved = ProviderConfigResolver.resolve(endpoint);
        TranslationProviderAdapter adapter = ProviderAdapterRegistry.forEndpoint(endpoint);
        ProviderRuntimeState runtimeState = ProviderRuntimeRegistry.forEndpoint(endpoint);

        ProviderSettings settings;
        boolean enabled;
        int priority;
        int maxRequestsPerMinute;

        if (endpoint == Config.EndPoint.CUSTOM) {
            // Matches Translator.resolveActiveProviderSettings' exact CUSTOM-branch shape (auth
            // mode string parsing included) -- that method is retired once Translator delegates to
            // TranslationRouter, at which point this becomes the one place doing it.
            AuthMode authMode = "NONE".equalsIgnoreCase(Config.CUSTOM_PROVIDER_AUTH_MODE.get())
                    ? AuthMode.NONE : AuthMode.BEARER;
            settings = new ProviderSettings(endpoint, resolved.apiKey(), resolved.model(),
                    Config.CUSTOM_PROVIDER_BASE_URL.get(), authMode, resolved.supportsVision());
            enabled = Config.CUSTOM_PROVIDER_ENABLED.get();
            priority = Config.CUSTOM_PROVIDER_PRIORITY.get();
            maxRequestsPerMinute = Config.CUSTOM_PROVIDER_MAX_REQUESTS_PER_MINUTE.get();
        } else {
            settings = new ProviderSettings(endpoint, resolved.apiKey(), resolved.model(), null, null,
                    resolved.supportsVision());
            Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(endpoint);
            enabled = keys.enabled().get();
            priority = keys.priority().get();
            maxRequestsPerMinute = keys.maxRequestsPerMinute().get();
        }

        return new ProviderCandidate(endpoint, adapter, settings, runtimeState, resolved.supportsVision(),
                priority, maxRequestsPerMinute, enabled);
    }
}
