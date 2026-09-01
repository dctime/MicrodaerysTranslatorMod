// Regression check for net.github.dctime.screen.PendingTranslatorConfig.sanitizeApiKey() (mailbox
// review #002 point E1: a pasted API key with a trailing newline crashed Test Connection's click
// handler synchronously -- HttpRequest.Builder.header() throws IllegalArgumentException on any
// control character in a header value -- and, once saved, crashed every real translation's
// tooltip render the same way).
//
// PendingTranslatorConfig otherwise imports net.github.dctime.Config (which pulls in NeoForge's
// ModConfigSpec machinery, not loadable headless), but sanitizeApiKey() itself touches neither
// Config nor any Minecraft/NeoForge class -- confirmed empirically, not assumed: this file's own
// Run instructions below deliberately point ONLY at build/classes/java/main, no NeoForge/Minecraft
// jars on the classpath at all, and it still runs. If a future edit makes sanitizeApiKey() (or
// anything this file's classloading path touches) reach into Config, this will fail with
// NoClassDefFoundError instead of silently starting to work only because a full classpath happened
// to be used -- that failure IS the point, not a bug in this test.
//
// Not covered here: every other method on PendingTranslatorConfig (all touch Config), and neither
// of the two places that actually CALL sanitizeApiKey() in the real GUI (TranslatorConfigScreen's
// EditBox responder and Paste button) -- those need a running game, like every other screen file.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-api-key-sanitize tools/verify-api-key-sanitize/VerifyApiKeySanitize.java
//   java -cp "tools/verify-api-key-sanitize:$MAIN_CLASSES" VerifyApiKeySanitize

import net.github.dctime.screen.PendingTranslatorConfig;

public class VerifyApiKeySanitize {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        assertTrue("trailing newline (the most common paste-from-webpage case) is stripped",
                PendingTranslatorConfig.sanitizeApiKey("AIzaSyABC123\n").equals("AIzaSyABC123"));
        assertTrue("leading/trailing whitespace is stripped",
                PendingTranslatorConfig.sanitizeApiKey("  AIzaSyABC123  ").equals("AIzaSyABC123"));
        assertTrue("CRLF (Windows clipboard) is stripped",
                PendingTranslatorConfig.sanitizeApiKey("AIzaSyABC123\r\n").equals("AIzaSyABC123"));
        assertTrue("an embedded space in the middle of the string is removed, not just trimmed from the ends",
                PendingTranslatorConfig.sanitizeApiKey("AIza Sy").equals("AIzaSy"));
        assertTrue("an embedded tab in the middle of the string is removed",
                PendingTranslatorConfig.sanitizeApiKey("AIza\tSy").equals("AIzaSy"));
        assertTrue("a clean key with no whitespace/control chars is returned unchanged",
                PendingTranslatorConfig.sanitizeApiKey("AIzaSyABC123-def_456").equals("AIzaSyABC123-def_456"));
        assertTrue("null input never throws (returns empty string, not NPE)",
                PendingTranslatorConfig.sanitizeApiKey(null).equals(""));
        assertTrue("empty input stays empty",
                PendingTranslatorConfig.sanitizeApiKey("").equals(""));

        System.out.println("ALL CHECKS PASSED");
    }
}
