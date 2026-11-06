package net.github.dctime.libs.provider;

import java.util.Map;

/**
 * Static per-provider parameters for {@link OpenAiCompatibleAdapter}. {@code baseUrl} must already
 * be normalized (no trailing slash) -- every built-in provider's is a compile-time constant, so
 * this is enforced by construction in {@code ProviderAdapterRegistry}; Custom Provider builds its
 * own spec per-request from {@link net.github.dctime.libs.BaseUrlUtil#normalize} instead of using
 * a static instance of this record.
 */
public record OpenAiCompatibleSpec(String baseUrl, String chatPath, String modelsPath,
                                    AuthMode authMode, Map<String, String> extraHeaders) {
}
