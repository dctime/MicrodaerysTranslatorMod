package net.github.dctime.libs;

import net.github.dctime.Config;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderAdapterRegistry;
import net.github.dctime.libs.provider.ProviderSettings;
import net.github.dctime.libs.provider.TranslationProviderAdapter;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * "Test Connection" for the config GUI: for each provider, hits a model-listing endpoint (NOT a
 * generation endpoint, unless the adapter's {@code supportsModelListing()} is false) with the
 * player's PENDING (not-yet-saved) endpoint/apiKey/model/[Custom base URL/auth mode], so it
 * doesn't cost generation quota/tokens for most providers and can be pressed freely before Done is
 * ever clicked.
 * <p>
 * Adapter-aware: this class no longer has any per-provider knowledge itself -- it looks up the
 * right {@link TranslationProviderAdapter} via {@link ProviderAdapterRegistry} and asks IT to
 * build the request and check whether the configured model appears in the response. Used to be a
 * hand-written exhaustive {@code switch} per provider here, completely independent from
 * {@link Translator}'s own (different) per-provider dispatch -- exactly the drift-prone
 * duplication this refactor removes.
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
     *                        display; -1 when there was no HTTP response at all (CANNOT_CONNECT or
     *                        INVALID_BASE_URL).
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
        test(endpoint, apiKey, model, null, null, callback);
    }

    /** Overload carrying Custom Provider's extra pending fields; ignored for every other endpoint. */
    public static void test(Config.EndPoint endpoint, String apiKey, String model,
                             String customBaseUrl, AuthMode customAuthMode, Consumer<Result> callback) {
        TranslationProviderAdapter adapter = ProviderAdapterRegistry.forEndpoint(endpoint);
        // supportsVision is irrelevant here -- a connection test never attaches an image.
        ProviderSettings settings = new ProviderSettings(endpoint, apiKey, model, customBaseUrl, customAuthMode, true);

        HttpRequest request;
        try {
            request = adapter.buildConnectionTestRequest(settings);
        } catch (IllegalArgumentException e) {
            // Custom Provider with a blank/malformed base URL -- caught here specifically so it
            // never reaches the render thread as an uncaught exception; every other provider's
            // base URL is a compile-time constant and can't trigger this.
            callback.accept(new Result(ConnectionTestStatus.Status.INVALID_BASE_URL, -1, false));
            return;
        }

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
                    boolean modelFound = status == ConnectionTestStatus.Status.OK
                            && adapter.modelAppearsInConnectionTestResponse(resp.body(), model);
                    callback.accept(new Result(status, resp.statusCode(), modelFound));
                });
    }
}
