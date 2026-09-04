package net.github.dctime.libs.provider;

import net.github.dctime.Config;

import javax.annotation.Nullable;

/**
 * The resolved, already-sanitized values needed to build a request -- decoupled from both
 * {@link Config} and {@code PendingTranslatorConfig} so the same values serve both a real
 * translation ({@link net.github.dctime.libs.Translator}) and a Test Connection
 * ({@link net.github.dctime.libs.TranslationConnectionTester}, which uses PENDING/not-yet-saved
 * values) without either of those depending on the other's package.
 * <p>
 * {@code customBaseUrl}/{@code customAuthMode} are only meaningful when {@code endpoint == CUSTOM};
 * every other adapter ignores them.
 * <p>
 * {@code supportsVision} (added mailbox review round 017, point O1): whether the CURRENT model can
 * accept image input -- for a built-in provider, {@link ProviderInfo#supportsVisionFor}'s answer
 * for the active preset (or {@code true} for a player-typed Custom Model id, see that method's
 * javadoc for why); for {@link Config.EndPoint#CUSTOM}, the player's own
 * {@code Config.CUSTOM_PROVIDER_SUPPORTS_VISION} toggle. Not consumed by any adapter's
 * {@code buildTranslationRequest} (the caller already decides whether to pass a non-null image at
 * all) -- it lives here anyway because {@link ProviderConfigResolver#resolve} is where apiKey/model
 * already get resolved together from the exact same per-provider data, and a second, separate
 * "where do I look up vision capability" code path would be exactly the kind of drift this refactor
 * exists to avoid. Ignored entirely by {@link net.github.dctime.libs.TranslationConnectionTester}
 * (a connection test never attaches an image).
 */
public record ProviderSettings(Config.EndPoint endpoint, String apiKey, String model,
                                @Nullable String customBaseUrl, @Nullable AuthMode customAuthMode,
                                boolean supportsVision) {

    /** Convenience for callers that don't care about vision capability (Test Connection) --
     *  defaults to {@code true} (never blocks anything), matching the same "don't judge what we
     *  don't have data for" default as {@link ProviderInfo#supportsVisionFor}. */
    public static ProviderSettings of(Config.EndPoint endpoint, String apiKey, String model) {
        return new ProviderSettings(endpoint, apiKey, model, null, null, true);
    }
}
