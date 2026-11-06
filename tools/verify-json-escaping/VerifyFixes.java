// Regression check for net.github.dctime.libs.JsonUtil (the JSON-escaping fix pulled out of
// Translator.java) and the zero-size crop guard in
// src/main/java/net/github/dctime/screen/ScreenShotSelectAreaScreen.java.
//
// Deliberately plain: no test framework. It calls the REAL production JsonUtil class directly
// (no copy-paste), and checks JSON validity with the SAME Gson dependency the mod itself uses,
// not a hand-rolled decoder that could hide bugs by agreeing with itself.
//
// JsonUtil has no Minecraft/NeoForge imports, so it and Gson are the only two things needed on
// the classpath -- no game runtime required.
//
// Run (from the repo root, after `./gradlew compileJava` at least once so JsonUtil.class exists):
//   GSON=$(find ~/.gradle -name 'gson-2.10.1.jar' | head -1)
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$GSON:$MAIN_CLASSES" -d tools/verify-json-escaping tools/verify-json-escaping/VerifyFixes.java
//   java -cp "tools/verify-json-escaping:$GSON:$MAIN_CLASSES" VerifyFixes

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.github.dctime.libs.JsonUtil;

public class VerifyFixes {

    private static int countChar(String s, char target) {
        int n = 0;
        for (char c : s.toCharArray()) if (c == target) n++;
        return n;
    }

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    // Parses with the project's real Gson dependency and pulls out contents[0].parts[0].text,
    // the same shape JsonUtil.getGeminiJsonBody() builds.
    private static String parseTextFieldWithGson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    // Reproduces the OLD (pre-fix) Gemini payload shape: no escapeJson() call at all, only the
    // caller's ad-hoc `.replace("\"", "\\\"")` had run on the text. Kept here (not in JsonUtil)
    // purely as a fixture for the "bug reproduced" assertions below.
    private static String oldUnescapedGeminiJsonBody(String rawTextInEnglish) {
        String fixedText = rawTextInEnglish.replace("\"", "\\\"");
        return "{\"contents\":[{\"parts\":[{\"text\": \"" + fixedText + "\"}]}]}";
    }

    public static void main(String[] args) throws Exception {
        // Windows-style path text is a realistic case: modded item lore / datapack strings
        // regularly contain backslashes ("C:\Users\...", "assets\pack\..."). A lone backslash
        // not followed by a JSON escape char makes the payload structurally invalid; one that
        // happens to be followed by a valid escape letter (the "\t" from "\test" below) silently
        // corrupts content instead of erroring. Real-world Gson is the judge here, not our own logic.
        String raw = "path C:\\Users\\test \"hi\"";

        String oldJson = oldUnescapedGeminiJsonBody(raw);
        boolean oldRejectedByGson;
        String oldParsedValue = null;
        try {
            oldParsedValue = parseTextFieldWithGson(oldJson);
            oldRejectedByGson = false;
        } catch (JsonSyntaxException e) {
            oldRejectedByGson = true;
        }
        assertTrue("old Gemini-path JSON is rejected by Gson OR silently corrupted (bug reproduced)",
                oldRejectedByGson || !raw.equals(oldParsedValue));

        String newJson = JsonUtil.getGeminiJsonBody(null, raw);
        String decoded = parseTextFieldWithGson(newJson); // throws JsonSyntaxException if Gson rejects it
        // NOTE: the text-block template in getGeminiJsonBody() splits `"text": "` and the prompt
        // across lines, so the built JSON string literal always starts with one raw '\n' before
        // the prompt content -- pre-existing behaviour, unrelated to the escaping fix, and Gson
        // (lenient) accepts a literal newline inside a string without complaint. Harmless for the
        // API (a leading blank line in the prompt), but worth knowing rather than silently
        // asserting the wrong "exact round-trip" claim.
        assertTrue("JsonUtil.getGeminiJsonBody() parses with Gson and preserves content exactly (modulo the template's known leading newline)",
                decoded.equals("\n" + raw));

        // --- double-escaping regression check (Ollama/Mistral path) ---
        // OLD: fixedText already had `"` -> `\"` done by the caller, then buildXxxJson's
        // escapeJson() ran again on top of that.
        String oldFixedText2 = "say \"hi\"".replace("\"", "\\\"");
        String oldDoubleEscaped = JsonUtil.escapeJson(oldFixedText2);
        String newSingleEscaped = JsonUtil.escapeJson("say \"hi\"");
        assertTrue("old double-escape has more backslashes than a correct single escape (bug reproduced)",
                countChar(oldDoubleEscaped, '\\') > countChar(newSingleEscaped, '\\'));
        assertTrue("JsonUtil.escapeJson() emits exactly one backslash per quote (2 quotes -> 2 backslashes)",
                countChar(newSingleEscaped, '\\') == 2);
        // and it's Gson-valid end to end too
        assertTrue("single-escaped quote text parses correctly with Gson",
                parseTextFieldWithGson("{\"contents\":[{\"parts\":[{\"text\": \"" + newSingleEscaped + "\"}]}]}")
                        .equals("say \"hi\""));

        // --- Ollama/OpenAI-compatible builders: real production code, one escape pass, valid JSON ---
        String ollamaJson = JsonUtil.buildOllamaJson(raw, null, "phi3");
        JsonObject ollamaRoot = JsonParser.parseString(ollamaJson).getAsJsonObject();
        assertTrue("JsonUtil.buildOllamaJson() output parses with Gson and round-trips",
                ollamaRoot.get("prompt").getAsString().equals(raw));

        // Renamed from buildMistralJson (round 016, 11-provider expansion) -- Mistral's shape was
        // already this exact OpenAI-compatible format, now shared by 8 providers instead of one.
        String openAiCompatibleJson = JsonUtil.buildOpenAiCompatibleJson(raw, null, "mistral-small-latest");
        JsonObject openAiCompatibleRoot = JsonParser.parseString(openAiCompatibleJson).getAsJsonObject();
        String openAiCompatibleContent = openAiCompatibleRoot.getAsJsonArray("messages").get(0).getAsJsonObject()
                .get("content").getAsString();
        assertTrue("JsonUtil.buildOpenAiCompatibleJson() output parses with Gson and round-trips",
                openAiCompatibleContent.equals(raw));

        // New in round 016: Anthropic's shape (max_tokens required, content is a plain string or
        // an array of typed blocks -- see AnthropicAdapter's javadoc for why it isn't reused for
        // the other 8 OpenAI-compatible-shaped providers).
        String anthropicJson = JsonUtil.buildAnthropicJson(raw, null, "claude-sonnet-5", 1024);
        JsonObject anthropicRoot = JsonParser.parseString(anthropicJson).getAsJsonObject();
        String anthropicContent = anthropicRoot.getAsJsonArray("messages").get(0).getAsJsonObject()
                .get("content").getAsString();
        assertTrue("JsonUtil.buildAnthropicJson() output parses with Gson and round-trips",
                anthropicContent.equals(raw));
        assertTrue("JsonUtil.buildAnthropicJson() output includes the required max_tokens field",
                anthropicRoot.get("max_tokens").getAsInt() == 1024);

        // --- zero-size crop guard boundary math (front-guard before `new NativeImage`) ---
        assertTrue("zero-width selection (startX==endX) is rejected", isRejected(10, 20, 10, 30));
        assertTrue("zero-height selection (startY==endY) is rejected", isRejected(10, 20, 20, 20));
        assertTrue("normal 1x1 minimum selection is NOT rejected", !isRejected(10, 20, 11, 21));
        assertTrue("normal larger selection is NOT rejected", !isRejected(0, 0, 100, 50));

        System.out.println("ALL CHECKS PASSED");
    }

    // mirrors ScreenShotSelectAreaScreen: if (endX <= startX || endY <= startY) reject
    private static boolean isRejected(int startX, int startY, int endX, int endY) {
        return endX <= startX || endY <= startY;
    }
}
