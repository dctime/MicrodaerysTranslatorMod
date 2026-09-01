package net.github.dctime.libs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.github.dctime.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * "Test Connection" for the config GUI: for each provider, hits a list-models endpoint (NOT a
 * generation endpoint) with the player's PENDING (not-yet-saved) endpoint/apiKey/model, so it
 * doesn't cost generation quota/tokens and can be pressed freely before Done is ever clicked.
 * <p>
 * Deliberately separate from {@link Translator}'s translation request path: doesn't touch
 * {@code translationCache}/{@code IN_FLIGHT}/the concurrency semaphore/the RPM rate limiter, and
 * carries no "a later tick will retry this" assumption -- the button itself is the only trigger,
 * disabled while a test is in flight, re-enabled once the result comes back.
 * <p>
 * Security: never logs the API key (it's sent as a header, exactly like {@link Translator}'s own
 * requests, never as a URL query parameter -- Java HTTP client exception messages can embed the
 * full request URI, so a key in the query string would leak into logs/latest.log the moment a
 * connection error is logged) and never surfaces a raw response body to the caller, only a
 * {@link ConnectionTestStatus.Status}.
 * <p>
 * NOT covered by any headless test (needs java.net.http + a real network call): this whole class.
 * Only {@link ConnectionTestStatus#fromHttpCode} is independently verified, in
 * tools/verify-connection-test-status.
 */
public class TranslationConnectionTester {

    /**
     * @param status          connection/authorization outcome; the headline result.
     * @param httpStatusCode  the raw HTTP status code, for HTTP_ERROR's "HTTP Error {code}"
     *                        display; -1 when there was no HTTP response at all (CANNOT_CONNECT).
     * @param modelFound      secondary hint only, meaningful when status == OK. False does NOT
     *                        mean the model can't be used -- list-models responses can be
     *                        paginated (Google) or use tags the player's model_name string doesn't
     *                        include (Ollama's "llama3:latest" vs a player typing "llama3"), so
     *                        this is a "not found in what we could see" hint, never promoted to a
     *                        failure.
     */
    public record Result(ConnectionTestStatus.Status status, int httpStatusCode, boolean modelFound) {}

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fires the request and returns immediately; {@code callback} runs on the HTTP client's own
     * callback thread, NOT the render thread -- callers must hop back via
     * {@code Minecraft.getInstance().execute(...)} themselves before touching any widget.
     */
    public static void test(Config.EndPoint endpoint, String apiKey, String model, Consumer<Result> callback) {
        HttpRequest request = buildRequest(endpoint, apiKey);

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, throwable) -> {
                    if (throwable != null) {
                        // Never pass the throwable (or its message) through: Java's HTTP client
                        // exception text often embeds the full request URI, and swallowing it
                        // here is what makes "API key only ever goes in a header, never a URL
                        // query param" actually airtight rather than just true today.
                        callback.accept(new Result(ConnectionTestStatus.Status.CANNOT_CONNECT, -1, false));
                        return;
                    }
                    ConnectionTestStatus.Status status = ConnectionTestStatus.fromHttpCode(resp.statusCode());
                    boolean modelFound = status == ConnectionTestStatus.Status.OK && modelAppearsIn(endpoint, resp.body(), model);
                    callback.accept(new Result(status, resp.statusCode(), modelFound));
                });
    }

    private static HttpRequest buildRequest(Config.EndPoint endpoint, String apiKey) {
        Duration timeout = Duration.ofSeconds(10);
        return switch (endpoint) {
            // x-goog-api-key header, matching Translator.setupRequest() -- not "?key=" in the URL
            // (Config.java's own comment notes the query-param form "works too", but the header
            // form is what this mod actually uses elsewhere, and is the only form that keeps the
            // key out of anything that logs a URI).
            case GOOGLE_AI_STUDIO -> HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models"))
                    .timeout(timeout)
                    .header("x-goog-api-key", apiKey)
                    .GET().build();
            case MISTRAL -> HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mistral.ai/v1/models"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .GET().build();
            // No API key: Ollama is a local, unauthenticated server (see Config.ENDPOINT_CONFIG's
            // OLLAMA case in Translator -- same host:port, no Authorization/x-goog-api-key header).
            case OLLAMA -> HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:11434/api/tags"))
                    .timeout(timeout)
                    .GET().build();
        };
    }

    /** Best-effort match against a list-models response; false on any parse failure (never throws). */
    private static boolean modelAppearsIn(Config.EndPoint endpoint, String responseBody, String model) {
        if (model == null || model.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            return switch (endpoint) {
                // {"models": [{"name": "models/gemini-1.5-flash", ...}, ...]} -- both the
                // response's names and the player's model id may or may not carry the "models/"
                // prefix, so strip it from both sides before comparing.
                case GOOGLE_AI_STUDIO -> streamNames(root, "models", "name")
                        .anyMatch(name -> stripPrefix(name, "models/").equals(stripPrefix(model, "models/")));
                // {"data": [{"id": "mistral-small-latest", ...}, ...]}
                case MISTRAL -> streamNames(root, "data", "id").anyMatch(id -> id.equals(model));
                // {"models": [{"name": "llama3:latest", ...}, ...]} -- Ollama tags every model
                // with ":latest" (or another tag). Stripping the tag from BOTH sides (as an
                // earlier version of this did) is a false-positive trap: a player who typed
                // "llama3:70b" but only has "llama3:latest" installed would match anyway (both
                // strip down to "llama3"), showing a green "Connected" with no warning even though
                // that exact model isn't there -- worse than not checking at all, since it's a
                // confident wrong answer instead of an honest "can't tell". So: if the player
                // wrote a tag, require an exact match (no stripping); only strip the RESPONSE
                // side's tag when the player didn't write one themselves (matching Ollama's own
                // "no tag = :latest" convention loosely -- any installed tag of that base name
                // counts, not just :latest specifically).
                case OLLAMA -> {
                    boolean playerSpecifiedTag = model.contains(":");
                    yield streamNames(root, "models", "name")
                            .anyMatch(name -> playerSpecifiedTag ? name.equals(model) : stripTag(name).equals(model));
                }
            };
        } catch (Exception e) {
            return false; // unexpected shape: treat as "couldn't confirm", never a crash
        }
    }

    private static java.util.stream.Stream<String> streamNames(JsonObject root, String arrayField, String nameField) {
        if (!root.has(arrayField) || !root.get(arrayField).isJsonArray()) return java.util.stream.Stream.empty();
        JsonArray array = root.getAsJsonArray(arrayField);
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(o -> o.has(nameField))
                .map(o -> o.get(nameField).getAsString());
    }

    private static String stripPrefix(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    private static String stripTag(String s) {
        int colon = s.indexOf(':');
        return colon < 0 ? s : s.substring(0, colon);
    }
}
