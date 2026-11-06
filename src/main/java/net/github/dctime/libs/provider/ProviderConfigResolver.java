package net.github.dctime.libs.provider;

import net.github.dctime.Config;

/**
 * Single source of truth for "what are this provider's live api_key/model right now" --
 * {@link net.github.dctime.libs.Translator} (building a real request) and
 * {@code PendingTranslatorConfig.loadFromConfig()} (seeding the GUI's editable copy) both go
 * through this instead of each re-implementing the legacy-migration fallback independently, which
 * is exactly the kind of two-copies-that-can-drift the per-provider persistence work is trying to
 * avoid in the first place.
 * <p>
 * Legacy migration: {@code Config.API_KEY}/{@code Config.MODEL_NAME} are the old flat keys a
 * pre-refactor player's TOML already has real values in. This class applies them as an in-memory
 * fallback ONLY for whichever provider {@code Config.ENDPOINT_CONFIG} currently names, and ONLY
 * while that provider's own new per-provider field is still blank (a player who has since actually
 * saved a per-provider value is respected, never silently overridden). Nothing here writes
 * anything back to {@link Config} -- this is read-only; {@code PendingTranslatorConfig.saveToConfig()}
 * is the only thing that persists the migrated value, and only once the player presses Done.
 */
public final class ProviderConfigResolver {

    private ProviderConfigResolver() {
    }

    public record ResolvedProviderConfig(String apiKey, String model, boolean supportsVision) {
    }

    public static ResolvedProviderConfig resolve(Config.EndPoint endpoint) {
        if (endpoint == Config.EndPoint.CUSTOM) {
            return new ResolvedProviderConfig(Config.CUSTOM_PROVIDER_API_KEY.get(), Config.CUSTOM_PROVIDER_MODEL.get(),
                    Config.CUSTOM_PROVIDER_SUPPORTS_VISION.get());
        }

        Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(endpoint);
        if (keys == null) {
            // Should be unreachable -- every EndPoint except CUSTOM is registered in
            // Config.PROVIDER_KEYS by construction, and MicrodaerysTranslatorClient's own startup
            // self-check (checkProviderRegistriesAreComplete) logs loudly if one is ever missing.
            // A bare NPE here would surface as an unexplained crash the first time this endpoint's
            // translation/GUI path runs, with no indication of WHY -- naming the endpoint turns
            // that into a self-explaining error instead (mailbox review round 016, point N2).
            throw new IllegalStateException("No Config.PROVIDER_KEYS entry for " + endpoint
                    + " -- this is a registration bug, not a player-reachable state.");
        }
        String apiKey = keys.apiKey() != null ? keys.apiKey().get() : "";
        String model = keys.model().get();

        if (endpoint == Config.ENDPOINT_CONFIG.get()) {
            if (apiKey.isBlank() && !Config.API_KEY.get().isBlank()) apiKey = Config.API_KEY.get();
            if (model.isBlank() && !Config.MODEL_NAME.get().isBlank()) model = Config.MODEL_NAME.get();
        }

        return new ResolvedProviderConfig(apiKey, model, ProviderInfo.supportsVisionFor(endpoint, model));
    }
}
