// Regression check for net.github.dctime.libs.BaseUrlUtil (added for the 11-provider expansion,
// Custom Provider's player-typed Base URL field). Covers mailbox review round 016 point M3: a
// blank or malformed Base URL builds a syntactically-legal RELATIVE URI (URI.create doesn't reject
// it), which HttpRequest.newBuilder().uri(...) then rejects with IllegalArgumentException --
// synchronously, on whatever thread asked for a request (Test Connection's click handler, or
// Translator's render-thread translation path). isValid() exists specifically to catch this BEFORE
// ever reaching HttpRequest, so the GUI can block Done and highlight the field red instead.
//
// Not covered here: OpenAiCompatibleAdapter's own request-building (needs java.net.http +
// Config.EndPoint, see tools/verify-provider-adapters instead) and the actual GUI red-highlight
// behavior in TranslatorConfigScreen (needs a running game).
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-base-url-normalize tools/verify-base-url-normalize/VerifyBaseUrlNormalize.java
//   java -cp "tools/verify-base-url-normalize:$MAIN_CLASSES" VerifyBaseUrlNormalize

import net.github.dctime.libs.BaseUrlUtil;

public class VerifyBaseUrlNormalize {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- normalize() ---
        assertTrue("trailing slash is stripped",
                BaseUrlUtil.normalize("http://localhost:8000/v1/").equals("http://localhost:8000/v1"));
        assertTrue("multiple trailing slashes are all stripped",
                BaseUrlUtil.normalize("http://localhost:8000/v1///").equals("http://localhost:8000/v1"));
        assertTrue("leading/trailing whitespace is stripped",
                BaseUrlUtil.normalize("  http://localhost:8000/v1  ").equals("http://localhost:8000/v1"));
        assertTrue("control characters (e.g. a pasted newline) are stripped",
                BaseUrlUtil.normalize("http://localhost:8000/v1\n").equals("http://localhost:8000/v1"));
        assertTrue("a URL with no trailing slash is unchanged",
                BaseUrlUtil.normalize("https://api.example.com/v1").equals("https://api.example.com/v1"));
        assertTrue("plain http:// is NOT rejected or rewritten to https:// -- local/LAN servers commonly have no TLS",
                BaseUrlUtil.normalize("http://192.168.1.50:8000").equals("http://192.168.1.50:8000"));
        assertTrue("null input never throws (returns empty string, not NPE)",
                BaseUrlUtil.normalize(null).equals(""));
        assertTrue("empty input stays empty",
                BaseUrlUtil.normalize("").equals(""));

        // --- join() ---
        assertTrue("join never produces a doubled slash regardless of trailing slash on the base",
                BaseUrlUtil.join("http://localhost:8000/v1", "/chat/completions")
                        .equals("http://localhost:8000/v1/chat/completions"));
        assertTrue("join adds a slash if the path is missing its leading one",
                BaseUrlUtil.join("http://localhost:8000/v1", "chat/completions")
                        .equals("http://localhost:8000/v1/chat/completions"));

        // --- isValid() -- the actual M3 regression coverage ---
        assertTrue("blank base URL is invalid (the most common M3 trigger: player picks Custom Provider, leaves Base URL empty)",
                !BaseUrlUtil.isValid(""));
        assertTrue("null base URL is invalid, never throws",
                !BaseUrlUtil.isValid(null));
        assertTrue("whitespace-only base URL is invalid",
                !BaseUrlUtil.isValid("   "));
        assertTrue("a relative-looking string (no scheme) is invalid",
                !BaseUrlUtil.isValid("localhost:8000/v1"));
        assertTrue("a malformed URL with an embedded space is invalid, not a thrown exception",
                !BaseUrlUtil.isValid("http://bad url"));
        assertTrue("a non-http(s) scheme is invalid",
                !BaseUrlUtil.isValid("ftp://example.com"));
        assertTrue("a valid https URL is valid",
                BaseUrlUtil.isValid("https://api.example.com/v1"));
        assertTrue("a valid plain http URL (local/LAN server) is valid",
                BaseUrlUtil.isValid("http://localhost:8000/v1"));
        assertTrue("a valid URL with a trailing slash is still valid (isValid normalizes internally)",
                BaseUrlUtil.isValid("https://api.example.com/v1/"));

        System.out.println("ALL CHECKS PASSED");
    }
}
