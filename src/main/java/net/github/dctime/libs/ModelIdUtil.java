package net.github.dctime.libs;

/**
 * Sanitizes a player-typed model id (Custom Model, or Custom Provider's Model field) -- Minecraft-
 * free so {@code tools/verify-*} can exercise it headless.
 * <p>
 * Deliberately narrow: only trims surrounding whitespace and strips control characters (the same
 * class of input -- newline/tab -- that can break an HTTP header or a JSON string). Real model ids
 * routinely contain {@code / : - _ .} (e.g. {@code openai/gpt-oss-20b}, {@code mistral-small-latest},
 * {@code qwen3:8b}, {@code gemini-3.5-flash-lite}), so none of those are touched or rejected. An
 * unrecognized-but-otherwise-valid model id must never be reset to blank or to a preset -- this
 * function only cleans, it never validates or judges the string.
 */
public final class ModelIdUtil {

    private ModelIdUtil() {
    }

    public static String sanitize(String rawModelId) {
        if (rawModelId == null) return "";
        return rawModelId.strip().replaceAll("\\p{Cntrl}", "");
    }
}
