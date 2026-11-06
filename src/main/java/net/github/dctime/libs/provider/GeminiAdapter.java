package net.github.dctime.libs.provider;

import net.github.dctime.libs.StringUtil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.github.dctime.Config;
import net.github.dctime.libs.ApiKeyUtil;
import net.github.dctime.libs.JsonUtil;

import javax.annotation.Nullable;
import java.net.URI;
import net.github.dctime.libs.http.HttpRequest;
import java.time.Duration;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Native Gemini API. Relocated verbatim from {@code Translator.setupRequest}/
 * {@code parseGeminiResponse} and {@code TranslationConnectionTester}'s GOOGLE_AI_STUDIO case --
 * no behavior change, same URL shape, same {@code x-goog-api-key} header (never a URL query
 * param -- keeps the key out of anything that ever logs a request URI).
 */
public final class GeminiAdapter implements TranslationProviderAdapter {

    @Override
    public Config.EndPoint type() {
        return Config.EndPoint.GOOGLE_AI_STUDIO;
    }

    @Override
    public boolean supportsModelListing() {
        return true;
    }

    @Override
    public HttpRequest buildTranslationRequest(ProviderSettings settings, String prompt,
                                                 @Nullable String imageBase64, boolean isScreenshot,
                                                 int timeoutSeconds) {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
                settings.model());
        String jsonBody = JsonUtil.getGeminiJsonBody(imageBase64, prompt);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-goog-api-key", ApiKeyUtil.sanitize(settings.apiKey()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    @Override
    public String parseTranslationResponse(String responseText) {
        JsonObject response = new JsonParser().parse(responseText).getAsJsonObject();
        return response.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    @Override
    public HttpRequest buildConnectionTestRequest(ProviderSettings settings) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models"))
                .timeout(Duration.ofSeconds(10))
                .header("x-goog-api-key", ApiKeyUtil.sanitize(settings.apiKey()))
                .GET().build();
    }

    @Override
    public boolean modelAppearsInConnectionTestResponse(String responseBody, String model) {
        if (model == null || StringUtil.isBlank(model)) return false;
        try {
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();
            return streamNames(root, "models", "name")
                    .anyMatch(name -> stripPrefix(name, "models/").equals(stripPrefix(model, "models/")));
        } catch (Exception e) {
            return false;
        }
    }

    private static Stream<String> streamNames(JsonObject root, String arrayField, String nameField) {
        if (!root.has(arrayField) || !root.get(arrayField).isJsonArray()) return Stream.empty();
        return StreamSupport.stream(root.getAsJsonArray(arrayField).spliterator(), false)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(o -> o.has(nameField))
                .map(o -> o.get(nameField).getAsString());
    }

    private static String stripPrefix(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }
}
