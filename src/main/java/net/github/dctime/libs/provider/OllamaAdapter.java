package net.github.dctime.libs.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.github.dctime.Config;
import net.github.dctime.libs.JsonUtil;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Local Ollama server. Relocated verbatim from {@code Translator.setupRequestOllama}/
 * {@code parseOllamaResponse} and {@code TranslationConnectionTester}'s OLLAMA case -- still
 * hardcoded {@code http://127.0.0.1:11434}, still {@code /api/generate}, no auth header. Left
 * unchanged on purpose (per the spec: don't change a working local-provider behavior just to look
 * more like the OpenAI-compatible providers).
 */
public final class OllamaAdapter implements TranslationProviderAdapter {

    private static final String BASE = "http://127.0.0.1:11434";

    @Override
    public Config.EndPoint type() {
        return Config.EndPoint.OLLAMA;
    }

    @Override
    public boolean supportsModelListing() {
        return true;
    }

    @Override
    public HttpRequest buildTranslationRequest(ProviderSettings settings, String prompt,
                                                 @Nullable String imageBase64, boolean isScreenshot,
                                                 int timeoutSeconds) {
        String jsonBody = JsonUtil.buildOllamaJson(prompt, imageBase64, settings.model());
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/generate"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    @Override
    public String parseTranslationResponse(String responseText) {
        JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();
        return root.has("response") ? root.get("response").getAsString() : null;
    }

    @Override
    public HttpRequest buildConnectionTestRequest(ProviderSettings settings) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/tags"))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
    }

    @Override
    public boolean modelAppearsInConnectionTestResponse(String responseBody, String model) {
        if (model == null || model.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!root.has("models") || !root.get("models").isJsonArray()) return false;
            // A player who typed a tag (e.g. "llama3:70b") must match exactly -- stripping tags on
            // both sides would let it match against an installed "llama3:latest" that isn't
            // actually the model they configured, a confident-but-wrong "Connected" instead of an
            // honest "can't tell". Only strip the RESPONSE side's tag when the player didn't write
            // one themselves.
            boolean playerSpecifiedTag = model.contains(":");
            for (JsonElement el : root.getAsJsonArray("models")) {
                if (!el.isJsonObject() || !el.getAsJsonObject().has("name")) continue;
                String name = el.getAsJsonObject().get("name").getAsString();
                if (playerSpecifiedTag ? name.equals(model) : stripTag(name).equals(model)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static String stripTag(String s) {
        int colon = s.indexOf(':');
        return colon < 0 ? s : s.substring(0, colon);
    }
}
