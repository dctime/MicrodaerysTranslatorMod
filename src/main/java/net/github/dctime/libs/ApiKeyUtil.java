package net.github.dctime.libs;

/**
 * Single source of truth for API key sanitization -- deliberately Minecraft-free so
 * {@code tools/verify-*} can exercise it headless.
 * <p>
 * Was previously duplicated as {@code PendingTranslatorConfig.sanitizeApiKey}; that copy now just
 * delegates here. A pasted or typed key ending in a newline/tab/space (extremely common -- copied
 * from a webpage or a text file) makes {@code HttpRequest.Builder.header(...)} throw
 * {@code IllegalArgumentException} SYNCHRONOUSLY, before any network call -- not just a failed
 * request, but a render-thread crash on every tooltip once such a key is saved (see mailbox review
 * #002 point E1). Sanitizing only in the GUI layer isn't enough on its own: a key already sitting in
 * an existing player's TOML from before this fix shipped would still crash the very first time
 * {@link Translator} builds a request from it, so every provider adapter re-sanitizes here as well,
 * immediately before any {@code header(...)} call -- {@link Translator} is the actual security
 * boundary, the GUI's own sanitization is a second, earlier line of defense, not the only one.
 * <p>
 * Keeps only visible, printable ASCII (0x21 '!' through 0x7E '~') -- an ALLOWLIST, not the original
 * blocklist-style {@code [\p{Cntrl}\s]} regex. Confirmed empirically (mailbox review round 016) that
 * the blocklist version missed a real crash: a smart quote (U+201C/U+201D) or a zero-width space
 * (U+200B) -- neither a control character nor whitespace under Java's {@code \s} -- also makes
 * {@code HttpRequest.Builder.header(...)} throw {@code IllegalArgumentException: invalid header
 * value}, the exact same failure mode as a raw newline. Every real API key across every provider
 * this mod supports is plain ASCII (alphanumeric plus a handful of symbols like {@code -_.}), so
 * this allowlist can't reject a legitimate key -- and it closes off "some other Unicode character
 * the header validator doesn't like" as a whole class of future report, not just the two found so
 * far.
 */
public final class ApiKeyUtil {

    private ApiKeyUtil() {
    }

    public static String sanitize(String rawApiKey) {
        if (rawApiKey == null) return "";
        StringBuilder sb = new StringBuilder(rawApiKey.length());
        for (int i = 0; i < rawApiKey.length(); i++) {
            char c = rawApiKey.charAt(i);
            if (c >= 0x21 && c <= 0x7E) sb.append(c);
        }
        return sb.toString();
    }
}
