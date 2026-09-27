// Regression check for net.github.dctime.libs.ModelIdUtil (added for the 11-provider expansion:
// Custom Model and Custom Provider's Model field). Deliberately narrow -- only trims and strips
// control characters, never rejects/reshapes real model-id punctuation (/ : - _ .), since an
// unrecognized-but-valid model id must never be reset (see mailbox review #002 point E2/acceptance
// test 9).
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-model-id-sanitize tools/verify-model-id-sanitize/VerifyModelIdSanitize.java
//   java -cp "tools/verify-model-id-sanitize:$MAIN_CLASSES" VerifyModelIdSanitize

import net.github.dctime.libs.ModelIdUtil;

public class VerifyModelIdSanitize {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        assertTrue("leading/trailing whitespace is stripped",
                ModelIdUtil.sanitize("  gpt-oss-20b  ").equals("gpt-oss-20b"));
        assertTrue("a trailing newline (pasted) is stripped",
                ModelIdUtil.sanitize("gpt-oss-20b\n").equals("gpt-oss-20b"));
        assertTrue("a real model id with a namespace slash is preserved exactly",
                ModelIdUtil.sanitize("openai/gpt-oss-20b").equals("openai/gpt-oss-20b"));
        assertTrue("a real model id with a colon tag (Ollama-style) is preserved exactly",
                ModelIdUtil.sanitize("qwen3:8b").equals("qwen3:8b"));
        assertTrue("a real model id with dots and dashes is preserved exactly",
                ModelIdUtil.sanitize("mistral-small-latest").equals("mistral-small-latest"));
        assertTrue("a real model id with an underscore is preserved exactly",
                ModelIdUtil.sanitize("gemini-3.5-flash-lite").equals("gemini-3.5-flash-lite"));
        assertTrue("an OpenRouter-style free-tier suffix is preserved exactly",
                ModelIdUtil.sanitize("nvidia/nemotron-3-ultra-550b-a55b:free").equals("nvidia/nemotron-3-ultra-550b-a55b:free"));
        assertTrue("an unrecognized-but-plausible custom model id is never rejected or reshaped",
                ModelIdUtil.sanitize("my-lab/finetuned-model-v7").equals("my-lab/finetuned-model-v7"));
        assertTrue("null input never throws (returns empty string, not NPE)",
                ModelIdUtil.sanitize(null).equals(""));
        assertTrue("empty input stays empty",
                ModelIdUtil.sanitize("").equals(""));

        System.out.println("ALL CHECKS PASSED");
    }
}
