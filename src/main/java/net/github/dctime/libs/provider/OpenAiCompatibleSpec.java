package net.github.dctime.libs.provider;

import java.util.Map;

/**
 * Static per-provider parameters for {@link OpenAiCompatibleAdapter}. {@code baseUrl} must already
 * be normalized (no trailing slash) -- every built-in provider's is a compile-time constant, so
 * this is enforced by construction in {@code ProviderAdapterRegistry}; Custom Provider builds its
 * own spec per-request from {@link net.github.dctime.libs.BaseUrlUtil#normalize} instead of using
 * a static instance of this record.
 */
public final class OpenAiCompatibleSpec {
    private final String baseUrl;
    private final String chatPath;
    private final String modelsPath;
    private final AuthMode authMode;
    private final Map<String, String> extraHeaders;

    public OpenAiCompatibleSpec(String baseUrl, String chatPath, String modelsPath, AuthMode authMode, Map<String, String> extraHeaders) {
        this.baseUrl = baseUrl;
        this.chatPath = chatPath;
        this.modelsPath = modelsPath;
        this.authMode = authMode;
        this.extraHeaders = extraHeaders;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String chatPath() {
        return chatPath;
    }

    public String modelsPath() {
        return modelsPath;
    }

    public AuthMode authMode() {
        return authMode;
    }

    public Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiCompatibleSpec other = (OpenAiCompatibleSpec) o;
        return java.util.Objects.equals(baseUrl, other.baseUrl)
                && java.util.Objects.equals(chatPath, other.chatPath)
                && java.util.Objects.equals(modelsPath, other.modelsPath)
                && java.util.Objects.equals(authMode, other.authMode)
                && java.util.Objects.equals(extraHeaders, other.extraHeaders);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(baseUrl, chatPath, modelsPath, authMode, extraHeaders);
    }

    @Override
    public String toString() {
        return "OpenAiCompatibleSpec[baseUrl=" + baseUrl + ", chatPath=" + chatPath + ", modelsPath=" + modelsPath + ", authMode=" + authMode + ", extraHeaders=" + extraHeaders + "]";
    }
}
