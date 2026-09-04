// Regression check for net.github.dctime.libs.ConnectionTestStatus (Test Connection's HTTP
// status code -> outcome mapping, part of the Config GUI redesign). Calls the REAL production
// class directly (no copy) -- ConnectionTestStatus has no Minecraft/NeoForge dependency, same as
// JsonUtil/RetryPolicy/TargetLanguage, so no game runtime is needed.
//
// Not covered here: TranslationConnectionTester itself (the actual HTTP calls, response parsing,
// and model-name matching against each provider's list-models response) needs java.net.http plus
// real network access to a real provider, and reads Config.EndPoint -- none of that is
// independently testable headless. This file only pins down the pure status-code mapping.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-connection-test-status tools/verify-connection-test-status/VerifyConnectionTestStatus.java
//   java -cp "tools/verify-connection-test-status:$MAIN_CLASSES" VerifyConnectionTestStatus

import net.github.dctime.libs.ConnectionTestStatus;
import net.github.dctime.libs.ConnectionTestStatus.Status;

public class VerifyConnectionTestStatus {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        assertTrue("200 -> OK", ConnectionTestStatus.fromHttpCode(200) == Status.OK);
        assertTrue("401 -> INVALID_KEY", ConnectionTestStatus.fromHttpCode(401) == Status.INVALID_KEY);
        assertTrue("403 -> INVALID_KEY", ConnectionTestStatus.fromHttpCode(403) == Status.INVALID_KEY);
        assertTrue("429 -> RATE_LIMITED", ConnectionTestStatus.fromHttpCode(429) == Status.RATE_LIMITED);
        assertTrue("500 -> HTTP_ERROR (not silently treated as OK)", ConnectionTestStatus.fromHttpCode(500) == Status.HTTP_ERROR);
        assertTrue("404 -> HTTP_ERROR", ConnectionTestStatus.fromHttpCode(404) == Status.HTTP_ERROR);
        assertTrue("0 (no real response) -> HTTP_ERROR, not a crash", ConnectionTestStatus.fromHttpCode(0) == Status.HTTP_ERROR);
        assertTrue("CANNOT_CONNECT is a real enum value, reserved for the network-failure path (fromHttpCode never returns it)",
                Status.valueOf("CANNOT_CONNECT") != null);

        System.out.println("ALL CHECKS PASSED");
    }
}
