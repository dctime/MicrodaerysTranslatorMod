package net.github.dctime.libs.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.github.dctime.Config;
import net.github.dctime.libs.ApiKeyUtil;
import net.github.dctime.libs.JsonUtil;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Native Anthropic Messages API. New (Claude wasn't supported before this refactor) -- gets its
 * own adapter rather than sharing {@link OpenAiCompatibleAdapter} because both the request and
 * response shapes genuinely differ: {@code max_tokens} is REQUIRED (the other three shapes treat
 * it as optional/absent), and a successful response is {@code content[].text} content blocks, not
 * {@code choices[0].message.content}.
 * <p>
 * Headers: {@code x-api-key} (never a URL query param) + {@code anthropic-version: 2023-06-01}
 * (confirmed still current as of this writing).
 * <p>
 * {@code supportsModelListing()} assumes {@code GET /v1/models} works the same way OpenAI's does --
 * this was NOT independently confirmed against a live call during this round (no Anthropic key was
 * available in this environment to test with). If it turns out to 404 in real testing, flip this
 * to {@code false}; {@link net.github.dctime.libs.TranslationConnectionTester} already falls back
 * to a minimal generation request in that case, no other code needs to change.
 */
public final class AnthropicAdapter implements TranslationProviderAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    // This mod's prompts/tooltip text are short; a modest cap keeps a worst-case response bounded
    // without ever being what actually limits a normal translation.
    private static final int MAX_TOKENS = 1024;

    @Override
    public Config.EndPoint type() {
        return Config.EndPoint.ANTHROPIC;
    }

    @Override
    public boolean supportsModelListing() {
        return true;
    }

    @Override
    public HttpRequest buildTranslationRequest(ProviderSettings settings, String prompt,
                                                 @Nullable String imageBase64, boolean isScreenshot,
                                                 int timeoutSeconds) {
        String jsonBody = JsonUtil.buildAnthropicJson(prompt, imageBase64, settings.model(), MAX_TOKENS);
        return HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("x-api-key", ApiKeyUtil.sanitize(settings.apiKey()))
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    @Override
    public String parseTranslationResponse(String responseText) {
        JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();
        if (!root.has("content") || !root.get("content").isJsonArray()) return null;
        JsonArray content = root.getAsJsonArray("content");
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : content) {
            if (!el.isJsonObject()) continue;
            JsonObject block = el.getAsJsonObject();
            if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text")) {
                sb.append(block.get("text").getAsString());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    @Override
    public HttpRequest buildConnectionTestRequest(ProviderSettings settings) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/models"))
                .timeout(Duration.ofSeconds(10))
                .header("x-api-key", ApiKeyUtil.sanitize(settings.apiKey()))
                .header("anthropic-version", ANTHROPIC_VERSION)
                .GET().build();
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
