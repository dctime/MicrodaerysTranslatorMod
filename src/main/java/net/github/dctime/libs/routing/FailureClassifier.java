package net.github.dctime.libs.routing;

import java.net.http.HttpTimeoutException;

/**
 * Pure status-code/exception -> {@link ProviderFailureType} mapping. No Minecraft/NeoForge/I-O
 * dependency on purpose, so it's directly unit-testable (see tools/verify-failure-classifier).
 */
public final class FailureClassifier {

    private FailureClassifier() {
    }

    /** For a completed HTTP response. Only meaningful for non-2xx status codes -- callers must
     *  check {@code statusCode} themselves before deciding this is even a failure. */
    public static ProviderFailureType classifyHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return ProviderFailureType.AUTH;
        if (statusCode == 429) return ProviderFailureType.RATE_LIMIT;
        if (statusCode == 400) return ProviderFailureType.BAD_REQUEST;
        if (statusCode >= 500) return ProviderFailureType.SERVER;
        return ProviderFailureType.UNKNOWN;
    }

    /** For a request that never got an HTTP response at all (whenComplete's throwable branch). */
    public static ProviderFailureType classifyThrowable(Throwable t) {
        Throwable cause = t;
        // CompletableFuture wraps async exceptions in CompletionException; unwrap one level so the
        // real cause (e.g. HttpTimeoutException) is what actually gets classified.
        if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof HttpTimeoutException) return ProviderFailureType.TIMEOUT;
        return ProviderFailureType.CONNECTION;
    }
}
