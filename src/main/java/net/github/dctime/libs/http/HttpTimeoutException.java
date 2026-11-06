package net.github.dctime.libs.http;

import java.io.IOException;

/**
 * Raised (wrapped in the future's {@code CompletionException}) when the connect or the read timed
 * out -- the Java 8 counterpart of {@code java.net.http.HttpTimeoutException}, so
 * {@code FailureClassifier} can keep telling TIMEOUT apart from CONNECTION.
 */
public class HttpTimeoutException extends IOException {
    public HttpTimeoutException(String message) {
        super(message);
    }
}
