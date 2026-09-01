package net.github.dctime.libs;

/**
 * Pure HTTP-status-code -> connection-test-outcome mapping, used by
 * {@link TranslationConnectionTester}. No Minecraft/NeoForge/Config dependency on purpose (same
 * reasoning as JsonUtil/RetryPolicy/TargetLanguage) -- this is the one piece of Test Connection
 * that can run headless, in tools/verify-connection-test-status, instead of only being
 * hand-verified in game.
 */
public class ConnectionTestStatus {

    public enum Status {
        OK,
        INVALID_KEY,
        RATE_LIMITED,
        /** Only reachable from a network failure (no HTTP response at all), never from fromHttpCode(). */
        CANNOT_CONNECT,
        HTTP_ERROR
    }

    public static Status fromHttpCode(int code) {
        if (code == 200) return Status.OK;
        if (code == 401 || code == 403) return Status.INVALID_KEY;
        if (code == 429) return Status.RATE_LIMITED;
        return Status.HTTP_ERROR;
    }
}
