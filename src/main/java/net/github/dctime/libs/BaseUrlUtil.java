package net.github.dctime.libs;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Normalizes a player-typed Custom Provider base URL -- Minecraft-free so {@code tools/verify-*}
 * can exercise it headless.
 * <p>
 * Trims whitespace, strips control characters (same rationale as {@link ApiKeyUtil}/
 * {@link ModelIdUtil} -- a stray newline breaks {@code HttpRequest} synchronously), and strips
 * trailing {@code /} characters so {@link #join(String, String)} never produces a doubled {@code //}
 * regardless of whether the player typed a trailing slash. Deliberately does NOT reject or rewrite
 * {@code http://} -- self-hosted OpenAI-compatible servers (vLLM, LM Studio, a school/lab endpoint,
 * a reverse proxy) commonly run on plain HTTP on localhost or a LAN, and this mod has no way to
 * know that's wrong for the player's own setup. Does NOT append or strip {@code /v1} -- whatever
 * the player typed as their base is used as-is; this mod only ever appends a fixed suffix like
 * {@code /chat/completions} on top of it, never guesses at inserting {@code /v1} itself.
 */
public final class BaseUrlUtil {

    private BaseUrlUtil() {
    }

    public static String normalize(String rawBaseUrl) {
        if (rawBaseUrl == null) return "";
        String cleaned = rawBaseUrl.strip().replaceAll("\\p{Cntrl}", "");
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    /** Joins an already-{@link #normalize(String)}d base with a path that starts with {@code /},
     *  guaranteeing exactly one {@code /} between them regardless of what the caller passes. */
    public static String join(String normalizedBase, String path) {
        String suffix = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + suffix;
    }

    /**
     * True only for a base URL that {@code HttpRequest.Builder.uri(...)} can actually accept
     * (absolute, {@code http}/{@code https}, non-empty host) -- {@link #normalize} alone does NOT
     * guarantee this (see mailbox review round 016, point M3): {@code normalize("")} returns
     * {@code ""}, and joining that with a path like {@code /chat/completions} produces a
     * syntactically legal RELATIVE URI ({@code URI.create} accepts it without complaint), which
     * {@code HttpRequest.newBuilder().uri(...)} then rejects with
     * {@code IllegalArgumentException: URI with undefined scheme} -- synchronously, on whatever
     * thread called it. Confirmed empirically (not just reasoned about): {@code URI.create("")},
     * {@code URI.create("/chat/completions")}, and {@code URI.create("ht!tp://bad url")} (a
     * malformed scheme/an embedded space) all either build successfully as non-absolute URIs or
     * throw at parse time -- either way, {@code HttpRequest}'s own {@code uri()} call is the one
     * that actually throws for the first two cases, not {@code URI.create} itself.
     * <p>
     * Meant to be called BEFORE ever reaching the network layer -- see
     * {@code TranslatorConfigScreen.handleDone()} (blocks Done the same way blank Custom Model
     * already does) -- but {@code OpenAiCompatibleAdapter} still independently guards against a
     * blank/malformed base URL reaching {@code URI.create} at request-build time too (a hand-edited
     * or pre-this-fix TOML value could bypass the GUI check entirely), so this is defense-in-depth,
     * not the only line of defense.
     */
    public static boolean isValid(String rawBaseUrl) {
        String normalized = normalize(rawBaseUrl);
        if (normalized.isBlank()) return false;
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
