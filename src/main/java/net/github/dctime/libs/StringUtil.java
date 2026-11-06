package net.github.dctime.libs;

/**
 * Java 8 equivalents of the Java 11 {@code String} methods the 2.0.0 code base was written
 * against ({@code isBlank}, {@code strip*}, {@code repeat}). Same semantics, including Unicode
 * whitespace ({@link Character#isWhitespace}, which is what the JDK 11 methods use -- NOT
 * {@code trim()}'s "anything <= U+0020" rule). No Minecraft/Forge import, so every headless
 * tools/verify-* program that loads a pure libs/ class can still load this one too.
 */
public final class StringUtil {

    private StringUtil() {
    }

    /** {@code s.isBlank()} -- true for empty or whitespace-only. Throws NPE on null, like the JDK. */
    public static boolean isBlank(String s) {
        return indexOfNonWhitespace(s) == s.length();
    }

    public static String strip(String s) {
        int start = indexOfNonWhitespace(s);
        if (start == s.length()) return "";
        int end = lastIndexOfNonWhitespace(s);
        return s.substring(start, end + 1);
    }

    public static String stripLeading(String s) {
        return s.substring(indexOfNonWhitespace(s));
    }

    public static String stripTrailing(String s) {
        return s.substring(0, lastIndexOfNonWhitespace(s) + 1);
    }

    public static String repeat(String s, int count) {
        if (count < 0) throw new IllegalArgumentException("count is negative: " + count);
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }

    private static int indexOfNonWhitespace(String s) {
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (!Character.isWhitespace(cp)) break;
            i += Character.charCount(cp);
        }
        return i;
    }

    private static int lastIndexOfNonWhitespace(String s) {
        int i = s.length();
        while (i > 0) {
            int cp = s.codePointBefore(i);
            if (!Character.isWhitespace(cp)) break;
            i -= Character.charCount(cp);
        }
        return i - 1;
    }
}
