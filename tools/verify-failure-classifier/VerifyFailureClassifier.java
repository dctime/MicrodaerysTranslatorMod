// Regression check for net.github.dctime.libs.routing.FailureClassifier -- the pure status-code /
// throwable -> ProviderFailureType mapping TranslationRouter uses to decide how each provider's
// runtime state (cooldown/authError/etc.) gets updated after a failed attempt. No Minecraft
// dependency, calls the REAL production class directly.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-failure-classifier tools/verify-failure-classifier/VerifyFailureClassifier.java
//   java -cp "tools/verify-failure-classifier:$MAIN_CLASSES" VerifyFailureClassifier

import net.github.dctime.libs.routing.FailureClassifier;
import net.github.dctime.libs.routing.ProviderFailureType;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.util.concurrent.CompletionException;

public class VerifyFailureClassifier {

    private static void assertEquals(String label, ProviderFailureType expected, ProviderFailureType actual) {
        if (expected != actual) throw new AssertionError("FAILED: " + label + " -- expected " + expected + " got " + actual);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        assertEquals("401 classifies as AUTH", ProviderFailureType.AUTH, FailureClassifier.classifyHttpStatus(401));
        assertEquals("403 classifies as AUTH", ProviderFailureType.AUTH, FailureClassifier.classifyHttpStatus(403));
        assertEquals("429 classifies as RATE_LIMIT", ProviderFailureType.RATE_LIMIT, FailureClassifier.classifyHttpStatus(429));
        assertEquals("400 classifies as BAD_REQUEST", ProviderFailureType.BAD_REQUEST, FailureClassifier.classifyHttpStatus(400));
        assertEquals("500 classifies as SERVER", ProviderFailureType.SERVER, FailureClassifier.classifyHttpStatus(500));
        assertEquals("503 classifies as SERVER", ProviderFailureType.SERVER, FailureClassifier.classifyHttpStatus(503));
        assertEquals("599 classifies as SERVER (>= 500 catch-all)", ProviderFailureType.SERVER, FailureClassifier.classifyHttpStatus(599));
        assertEquals("404 classifies as UNKNOWN (not one of the specially-handled codes)",
                ProviderFailureType.UNKNOWN, FailureClassifier.classifyHttpStatus(404));
        assertEquals("200 classifies as UNKNOWN (classifyHttpStatus is only ever called for non-2xx by TranslationRouter, "
                        + "but must not crash or misclassify if it ever is)",
                ProviderFailureType.UNKNOWN, FailureClassifier.classifyHttpStatus(200));

        assertEquals("HttpTimeoutException classifies as TIMEOUT", ProviderFailureType.TIMEOUT,
                FailureClassifier.classifyThrowable(new HttpTimeoutException("timed out")));
        assertEquals("HttpConnectTimeoutException (a subclass of HttpTimeoutException) also classifies as TIMEOUT",
                ProviderFailureType.TIMEOUT, FailureClassifier.classifyThrowable(new HttpConnectTimeoutException("connect timed out")));
        assertEquals("a plain IOException (e.g. connection refused) classifies as CONNECTION",
                ProviderFailureType.CONNECTION, FailureClassifier.classifyThrowable(new IOException("Connection refused")));

        // CompletableFuture.whenComplete unwraps single-stage exceptions itself, but a chained/
        // composed future can still hand this method a CompletionException wrapper -- must unwrap
        // to the real cause, not misclassify every wrapped timeout as a generic CONNECTION failure.
        assertEquals("a HttpTimeoutException wrapped in CompletionException still classifies as TIMEOUT (unwrapped)",
                ProviderFailureType.TIMEOUT,
                FailureClassifier.classifyThrowable(new CompletionException(new HttpTimeoutException("timed out"))));

        System.out.println("ALL CHECKS PASSED");
    }
}
