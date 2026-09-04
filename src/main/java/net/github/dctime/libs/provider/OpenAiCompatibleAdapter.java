package net.github.dctime.libs.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.github.dctime.Config;
import net.github.dctime.libs.ApiKeyUtil;
import net.github.dctime.libs.BaseUrlUtil;
import net.github.dctime.libs.JsonUtil;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Shared adapter for every provider whose API mirrors OpenAI's {@code /chat/completions} shape:
 * NVIDIA NIM, Groq, OpenRouter, Mistral, DeepSeek, Cerebras, OpenAI itself, and Custom Provider.
 * Was going to be 8 near-identical {@code buildGroqJson}/{@code buildDeepSeekJson}/... methods --
 * this class plus {@link OpenAiCompatibleSpec} is what avoids that, per the spec's explicit
 * instruction not to duplicate 95%-identical provider request logic.
 * <p>
 * Every built-in provider (all but Custom) is constructed with a fixed {@link OpenAiCompatibleSpec}
 * from {@code ProviderAdapterRegistry}. Custom Provider is constructed with {@code staticSpec ==
 * null} and instead resolves its base URL/auth mode fresh from {@link ProviderSettings} on every
 * call ({@link #resolveSpec}), since those are player-typed and can change between requests
 * (Test Connection must use pending, not-yet-saved values -- see the class javadoc on
 * {@code TranslationConnectionTester}).
 */
public final class OpenAiCompatibleAdapter implements TranslationProviderAdapter {

    private final Config.EndPoint endpoint;
    @Nullable
    private final OpenAiCompatibleSpec staticSpec;
    private final boolean supportsModelListing;

    public OpenAiCompatibleAdapter(Config.EndPoint endpoint, @Nullable OpenAiCompatibleSpec staticSpec,
                                    boolean supportsModelListing) {
        this.endpoint = endpoint;
        this.staticSpec = staticSpec;
        this.supportsModelListing = supportsModelListing;
    }

    @Override
    public Config.EndPoint type() {
        return endpoint;
    }

    @Override
    public boolean supportsModelListing() {
        return supportsModelListing;
    }

    /**
     * Throws {@link IllegalArgumentException} for Custom Provider with a blank/malformed base URL
     * -- callers (both {@link net.github.dctime.libs.Translator} and
     * {@link net.github.dctime.libs.TranslationConnectionTester}) must catch this around any call
     * into this adapter and degrade safely (never let it reach the render thread as an uncaught
     * exception), since a player can type anything into the Custom Provider's Base URL field.
     */
    private OpenAiCompatibleSpec resolveSpec(ProviderSettings settings) {
        if (endpoint != Config.EndPoint.CUSTOM) {
            return staticSpec;
        }
        String base = BaseUrlUtil.normalize(settings.customBaseUrl());
        if (base.isBlank()) {
            throw new IllegalArgumentException("Custom Provider base URL is blank");
        }
        AuthMode authMode = settings.customAuthMode() != null ? settings.customAuthMode() : AuthMode.BEARER;
        return new OpenAiCompatibleSpec(base, "/chat/completions", "/models", authMode, java.util.Map.of());
    }

    private void applyAuth(HttpRequest.Builder builder, OpenAiCompatibleSpec spec, ProviderSettings settings) {
        if (spec.authMode() == AuthMode.BEARER) {
            builder.header("Authorization", "Bearer " + ApiKeyUtil.sanitize(settings.apiKey()));
        }
        // NONE: send no Authorization header at all, per spec (supports LAN/self-hosted servers
        // with no auth in front of them).
        spec.extraHeaders().forEach(builder::header);
    }

    @Override
    public HttpRequest buildTranslationRequest(ProviderSettings settings, String prompt,
                                                 @Nullable String imageBase64, boolean isScreenshot,
                                                 int timeoutSeconds) {
        OpenAiCompatibleSpec spec = resolveSpec(settings);
        String url = BaseUrlUtil.join(spec.baseUrl(), spec.chatPath());
        String jsonBody = JsonUtil.buildOpenAiCompatibleJson(prompt, imageBase64, settings.model());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");
        applyAuth(builder, spec, settings);
        return builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
    }

    @Override
    public String parseTranslationResponse(String responseText) {
        JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();
        if (!root.has("choices")) return null;
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty()) return null;
        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        if (!firstChoice.has("message")) return null;
        JsonObject message = firstChoice.getAsJsonObject("message");
        return message.has("content") ? message.get("content").getAsString() : null;
    }

    @Override
    public HttpRequest buildConnectionTestRequest(ProviderSettings settings) {
        OpenAiCompatibleSpec spec = resolveSpec(settings);
        if (!supportsModelListing) {
            // No provider registered today actually takes this path (see the interface javadoc),
            // but it's implemented so a future provider without a models endpoint doesn't need a
            // second adapter class: fall back to the cheapest possible generation request.
            return buildTranslationRequest(settings, "Reply with OK only.", null, false, 10);
        }
        String url = BaseUrlUtil.join(spec.baseUrl(), spec.modelsPath());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        applyAuth(builder, spec, settings);
        return builder.build();
    }

    @Override
    public boolean modelAppearsInConnectionTestResponse(String responseBody, String model) {
        if (model == null || model.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!root.has("data") || !root.get("data").isJsonArray()) return false;
            for (JsonElement el : root.getAsJsonArray("data")) {
                if (el.isJsonObject() && el.getAsJsonObject().has("id")
                        && el.getAsJsonObject().get("id").getAsString().equals(model)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
