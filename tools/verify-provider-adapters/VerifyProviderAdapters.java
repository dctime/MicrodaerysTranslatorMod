// Regression check for net.github.dctime.libs.provider.* -- the adapter layer that replaced the
// three independent, non-synchronized per-provider dispatch points this refactor removed (see
// TranslationProviderAdapter's own javadoc): Translator's request-building if/else (silent Google
// fallback for any unmatched value), Translator's response-shape SNIFFING if/else (also silent
// Gemini fallback), and TranslationConnectionTester's own separate switch. Exercises every adapter
// implementation's request building (URL shape, header presence/absence, sanitized API key) and
// response parsing (both translation responses and model-listing responses) against hand-built
// sample payloads -- all fully offline: java.net.http.HttpRequest can be built and introspected
// (uri()/headers()/method()) without ever sending it, so no real network call happens here.
//
// Confirmed headless-loadable without the NeoForge/Minecraft classpath (config.EndPoint is a
// nested enum with no static dependency on Config's own ModConfigSpec-building <clinit> -- see
// mailbox review round 016): this file's own Run instructions below deliberately omit any
// NeoForge/Minecraft jar, and it still runs. If a future edit makes any of libs/provider/*
// (or Config.EndPoint itself) reach into Config's static state, this will fail with
// NoClassDefFoundError instead of silently starting to work only because a full classpath happened
// to be used -- that failure IS the point.
//
// Not covered here: an actual network call to any real provider (every provider except Google AI
// Studio was never live-tested against a real API key in this environment -- see the completion
// report's section N), ProviderAdapterRegistry's registration for CUSTOM specifically routing
// through OpenAiCompatibleAdapter's per-request resolveSpec path in a live GUI context (needs a
// running game), and Translator's/TranslationConnectionTester's own try/catch around these calls
// (both are exercised by inspection in this file's comments, not executed, since those classes
// have Minecraft dependencies elsewhere in the same file). Also NOT covered:
// ProviderConfigResolver.resolve() itself (confirmed it needs the real NeoForge classpath -- see
// visionCapabilityIsResolvedCorrectly()'s javadoc) -- only ProviderInfo.supportsVisionFor(), the
// pure half of that lookup, is exercised here.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   GSON=$(find ~/.gradle -name 'gson-2.10.1.jar' | head -1)
//   javac -cp "$GSON:$MAIN_CLASSES" -d tools/verify-provider-adapters tools/verify-provider-adapters/VerifyProviderAdapters.java
//   java -cp "tools/verify-provider-adapters:$GSON:$MAIN_CLASSES" VerifyProviderAdapters

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.github.dctime.Config;
import net.github.dctime.libs.provider.*;

import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class VerifyProviderAdapters {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    private static void assertThrows(String label, Runnable r) {
        try {
            r.run();
            throw new AssertionError("FAILED (expected an exception): " + label);
        } catch (IllegalArgumentException e) {
            System.out.println("OK: " + label + " (threw " + e.getClass().getSimpleName() + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        registryCoversEveryEndpoint();
        providerInfoIsCompleteAndLangKeysExist();
        visionCapabilityIsResolvedCorrectly();
        geminiAdapter();
        ollamaAdapter();
        anthropicAdapter();
        openAiCompatibleAdapter_staticSpec();
        openAiCompatibleAdapter_customProvider();
        apiKeyNeverLeaksUnsanitizedIntoAHeader();

        System.out.println("ALL CHECKS PASSED");
    }

    private static void registryCoversEveryEndpoint() {
        for (Config.EndPoint endpoint : Config.EndPoint.values()) {
            TranslationProviderAdapter adapter = ProviderAdapterRegistry.forEndpoint(endpoint);
            assertTrue("ProviderAdapterRegistry has an adapter for " + endpoint + " and it reports its own type correctly",
                    adapter.type() == endpoint);
        }
    }

    /**
     * Added per mailbox review round 016, point N1: M1's whole point was making
     * {@link ProviderInfo} headless-testable (it used to hold a {@code Component}, pulling in
     * Minecraft) -- but nothing actually exercised it until this method. Checks two of the four
     * "a provider must be registered everywhere" failure modes {@code
     * checkProviderRegistriesAreComplete} covers at runtime: {@link ProviderInfo#of} not throwing
     * for any {@link Config.EndPoint}, and every {@link ProviderInfo#displayNameKey()} actually
     * existing in en_us.json -- the two are no longer compile-time linked now that
     * {@code ProviderInfo}'s MODID prefix is a hardcoded literal, not an import of
     * {@code MicrodaerysTranslatorClient.MODID}, specifically so this class could stay
     * Minecraft-free; a typo or a future MODID change would otherwise show up only as raw lang
     * keys in the player's dropdown, with build/verify all green.
     * <p>
     * Deliberately does NOT check {@code ModelPreset.displayName} against lang keys: unlike
     * {@code ProviderInfo.displayNameKey}, {@code ModelPreset.displayName} is a plain literal
     * brand/proper name (e.g. "GPT OSS 20B"), never a translation key -- see
     * {@code ProviderInfo}'s own class javadoc. There is nothing to look up for those.
     */
    private static void providerInfoIsCompleteAndLangKeysExist() throws Exception {
        String json = Files.readString(Path.of("src/generated/resources/assets/microdaerystranslator/lang/en_us.json"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Set<String> langKeys = root.keySet();

        for (Config.EndPoint endpoint : Config.EndPoint.values()) {
            ProviderInfo info = ProviderInfo.of(endpoint); // throws IllegalArgumentException if missing -- a loud test failure
            assertTrue("ProviderInfo.of(" + endpoint + ") reports its own endpoint correctly",
                    info.endpoint() == endpoint);
            assertTrue("ProviderInfo." + endpoint + ".displayNameKey() (\"" + info.displayNameKey()
                            + "\") actually exists in en_us.json",
                    langKeys.contains(info.displayNameKey()));
        }
    }

    private static void geminiAdapter() {
        GeminiAdapter adapter = new GeminiAdapter();
        ProviderSettings settings = ProviderSettings.of(Config.EndPoint.GOOGLE_AI_STUDIO, "AIzaSyTestKey", "gemini-3.5-flash-lite");

        HttpRequest req = adapter.buildTranslationRequest(settings, "translate: hello", null, false, 30);
        assertTrue("Gemini request URL embeds the model id",
                req.uri().toString().contains("gemini-3.5-flash-lite"));
        assertTrue("Gemini request uses x-goog-api-key header, never a URL query param",
                req.headers().firstValue("x-goog-api-key").orElse("").equals("AIzaSyTestKey")
                        && !req.uri().toString().contains("AIzaSyTestKey"));
        assertTrue("Gemini request has no Authorization header (uses x-goog-api-key instead)",
                req.headers().firstValue("Authorization").isEmpty());

        String sample = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"你好\"}]}}]}";
        assertTrue("Gemini response parsing extracts the nested text field",
                adapter.parseTranslationResponse(sample).equals("你好"));

        HttpRequest testReq = adapter.buildConnectionTestRequest(settings);
        assertTrue("Gemini connection-test request hits the models-list endpoint",
                testReq.uri().toString().equals("https://generativelanguage.googleapis.com/v1beta/models"));

        String modelsList = "{\"models\":[{\"name\":\"models/gemini-3.5-flash-lite\"},{\"name\":\"models/gemini-2.5-pro\"}]}";
        assertTrue("Gemini model-list match strips the 'models/' prefix on both sides",
                adapter.modelAppearsInConnectionTestResponse(modelsList, "gemini-3.5-flash-lite"));
        assertTrue("Gemini model-list match is false for a model not in the list (never a false positive)",
                !adapter.modelAppearsInConnectionTestResponse(modelsList, "gemini-9.9-nonexistent"));
    }

    private static void ollamaAdapter() {
        OllamaAdapter adapter = new OllamaAdapter();
        ProviderSettings settings = ProviderSettings.of(Config.EndPoint.OLLAMA, "", "qwen3:8b");

        HttpRequest req = adapter.buildTranslationRequest(settings, "translate: hello", null, false, 30);
        assertTrue("Ollama request targets the local server's /api/generate",
                req.uri().toString().equals("http://127.0.0.1:11434/api/generate"));
        assertTrue("Ollama request has no Authorization header (local, unauthenticated)",
                req.headers().firstValue("Authorization").isEmpty());

        String sample = "{\"model\":\"qwen3:8b\",\"response\":\"你好\",\"done\":true}";
        assertTrue("Ollama response parsing extracts the 'response' field",
                adapter.parseTranslationResponse(sample).equals("你好"));

        HttpRequest testReq = adapter.buildConnectionTestRequest(settings);
        assertTrue("Ollama connection-test request hits /api/tags",
                testReq.uri().toString().equals("http://127.0.0.1:11434/api/tags"));

        String modelsList = "{\"models\":[{\"name\":\"llama3:latest\"}]}";
        assertTrue("Ollama model-list match strips the response's tag when the player didn't specify one",
                adapter.modelAppearsInConnectionTestResponse(modelsList, "llama3"));
        assertTrue("Ollama model-list match requires an EXACT match when the player DID specify a tag " +
                        "(never a false-positive 'Connected' for a tag that isn't actually installed)",
                !adapter.modelAppearsInConnectionTestResponse(modelsList, "llama3:70b"));
    }

    private static void anthropicAdapter() {
        AnthropicAdapter adapter = new AnthropicAdapter();
        ProviderSettings settings = ProviderSettings.of(Config.EndPoint.ANTHROPIC, "sk-ant-test", "claude-sonnet-5");

        HttpRequest req = adapter.buildTranslationRequest(settings, "translate: hello", null, false, 30);
        assertTrue("Anthropic request targets the Messages API",
                req.uri().toString().equals("https://api.anthropic.com/v1/messages"));
        assertTrue("Anthropic request uses x-api-key, never a URL query param",
                req.headers().firstValue("x-api-key").orElse("").equals("sk-ant-test"));
        assertTrue("Anthropic request includes the required anthropic-version header",
                req.headers().firstValue("anthropic-version").orElse("").equals("2023-06-01"));
        String body = req.bodyPublisher().isPresent() ? bodyOf(req) : "";
        assertTrue("Anthropic request body includes max_tokens (the API REQUIRES it, unlike the other 3 shapes)",
                body.contains("\"max_tokens\""));

        String sample = "{\"content\":[{\"type\":\"text\",\"text\":\"你\"},{\"type\":\"tool_use\",\"id\":\"x\"},{\"type\":\"text\",\"text\":\"好\"}]}";
        assertTrue("Anthropic response parsing concatenates ALL text blocks and skips non-text blocks",
                adapter.parseTranslationResponse(sample).equals("你好"));

        String modelsList = "{\"data\":[{\"id\":\"claude-sonnet-5\"},{\"id\":\"claude-opus-5\"}]}";
        assertTrue("Anthropic model-list match on exact id",
                adapter.modelAppearsInConnectionTestResponse(modelsList, "claude-sonnet-5"));
        assertTrue("Anthropic model-list match is false for a model not in the list",
                !adapter.modelAppearsInConnectionTestResponse(modelsList, "claude-haiku-4-5-20251001"));
    }

    private static void openAiCompatibleAdapter_staticSpec() {
        // A representative static-spec instance -- same shape ProviderAdapterRegistry builds for
        // NVIDIA/Groq/OpenRouter/Mistral/DeepSeek/Cerebras/OpenAI, just constructed directly here
        // so this test doesn't depend on the registry's exact base URLs.
        OpenAiCompatibleSpec spec = new OpenAiCompatibleSpec("https://api.groq.com/openai/v1",
                "/chat/completions", "/models", AuthMode.BEARER, java.util.Map.of());
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(Config.EndPoint.GROQ, spec, true);
        ProviderSettings settings = ProviderSettings.of(Config.EndPoint.GROQ, "gsk_test", "openai/gpt-oss-20b");

        HttpRequest req = adapter.buildTranslationRequest(settings, "translate: hello", null, false, 30);
        assertTrue("OpenAI-compatible request URL joins base+path with exactly one slash",
                req.uri().toString().equals("https://api.groq.com/openai/v1/chat/completions"));
        assertTrue("OpenAI-compatible request uses Bearer auth",
                req.headers().firstValue("Authorization").orElse("").equals("Bearer gsk_test"));

        String sample = "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}";
        assertTrue("OpenAI-compatible response parsing extracts choices[0].message.content",
                adapter.parseTranslationResponse(sample).equals("你好"));

        HttpRequest testReq = adapter.buildConnectionTestRequest(settings);
        assertTrue("OpenAI-compatible connection-test request hits the models path",
                testReq.uri().toString().equals("https://api.groq.com/openai/v1/models"));

        String modelsList = "{\"data\":[{\"id\":\"openai/gpt-oss-20b\"}]}";
        assertTrue("OpenAI-compatible model-list match on exact id",
                adapter.modelAppearsInConnectionTestResponse(modelsList, "openai/gpt-oss-20b"));
    }

    private static void openAiCompatibleAdapter_customProvider() {
        // Custom Provider: no static spec -- resolved fresh from ProviderSettings every call.
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(Config.EndPoint.CUSTOM, null, true);

        // --- M3 regression coverage: this is the same bug class as E1, just on a new field ---
        ProviderSettings blank = new ProviderSettings(Config.EndPoint.CUSTOM, "key", "model", "", AuthMode.BEARER, true);
        assertThrows("a blank Custom Provider base URL throws before ever reaching URI.create/HttpRequest " +
                        "(the exact scenario mailbox review round 016 point M3 found: pick Custom Provider, " +
                        "leave Base URL empty, press Done)",
                () -> adapter.buildTranslationRequest(blank, "hi", null, false, 30));

        ProviderSettings malformed = new ProviderSettings(Config.EndPoint.CUSTOM, "key", "model", "http://bad url", AuthMode.BEARER, true);
        assertThrows("a malformed Custom Provider base URL (embedded space) throws instead of silently " +
                        "building a broken request",
                () -> adapter.buildTranslationRequest(malformed, "hi", null, false, 30));

        // --- Bearer vs None auth (acceptance tests 6/7) ---
        ProviderSettings bearer = new ProviderSettings(Config.EndPoint.CUSTOM, "sk-test", "my-model",
                "http://localhost:8000/v1", AuthMode.BEARER, true);
        HttpRequest bearerReq = adapter.buildTranslationRequest(bearer, "hi", null, false, 30);
        assertTrue("Custom Provider with Authentication=Bearer sends an Authorization header",
                bearerReq.headers().firstValue("Authorization").orElse("").equals("Bearer sk-test"));
        assertTrue("Custom Provider request URL is exactly base+/chat/completions, plain http:// preserved " +
                        "(local/LAN servers commonly have no TLS)",
                bearerReq.uri().toString().equals("http://localhost:8000/v1/chat/completions"));

        ProviderSettings none = new ProviderSettings(Config.EndPoint.CUSTOM, "sk-test", "my-model",
                "http://localhost:8000/v1", AuthMode.NONE, true);
        HttpRequest noneReq = adapter.buildTranslationRequest(none, "hi", null, false, 30);
        assertTrue("Custom Provider with Authentication=None sends NO Authorization header at all",
                noneReq.headers().firstValue("Authorization").isEmpty());
    }

    /**
     * Direct coverage for mailbox review round 017, point O1: {@code supportsVision} was being
     * collected per-model but never actually consulted before this fix. Only covers
     * {@link ProviderInfo#supportsVisionFor} itself -- deliberately NOT
     * {@link ProviderConfigResolver#resolve}, even though that's the method
     * {@code Translator.resolveActiveProviderSettings} actually calls: confirmed empirically (not
     * assumed) that calling it triggers {@code Config}'s own {@code <clinit>}, which needs
     * {@code net.neoforged.neoforge.common.ModConfigSpec$Builder} and throws
     * {@code NoClassDefFoundError} on this file's deliberately NeoForge-free classpath -- the same
     * failure this file's header comment already documents as "the point, not a bug" for
     * {@code Config.EndPoint} itself, just reached from a different call path this time
     * ({@code ProviderConfigResolver} reads {@code Config.PROVIDER_KEYS}/{@code
     * Config.CUSTOM_PROVIDER_*}, which are real {@code ModConfigSpec} values, not just the
     * dependency-free nested enum). {@code ProviderConfigResolver}'s propagation of this value is
     * therefore compile-verified and manually in-game tested only, not headless-tested.
     */
    private static void visionCapabilityIsResolvedCorrectly() {
        assertTrue("a known vision-capable preset (Gemini 3.5 Flash Lite) reports supportsVision=true",
                ProviderInfo.supportsVisionFor(Config.EndPoint.GOOGLE_AI_STUDIO, "gemini-3.5-flash-lite"));
        assertTrue("a known text-only preset (NVIDIA Nemotron 3 Ultra) reports supportsVision=false",
                !ProviderInfo.supportsVisionFor(Config.EndPoint.NVIDIA_NIM, "nvidia/nemotron-3-ultra-550b-a55b"));
        assertTrue("an unrecognized (player-typed Custom Model) id defaults to supportsVision=true -- " +
                        "never judge what we have no data for, matches pre-existing behavior",
                ProviderInfo.supportsVisionFor(Config.EndPoint.NVIDIA_NIM, "some-custom-finetune"));
    }

    private static void apiKeyNeverLeaksUnsanitizedIntoAHeader() {
        // Defense-in-depth check (mailbox review round 016 point M1's own acknowledgment that
        // Translator, not just the GUI, is the real security boundary): every adapter re-sanitizes
        // via ApiKeyUtil at request-build time, so even a dirty key already sitting in an existing
        // player's TOML from before this fix shipped can't crash the first real request built from
        // it.
        String dirtyKey = "AIza\nSyTestKey";
        GeminiAdapter gemini = new GeminiAdapter();
        HttpRequest req = gemini.buildTranslationRequest(
                ProviderSettings.of(Config.EndPoint.GOOGLE_AI_STUDIO, dirtyKey, "gemini-3.5-flash-lite"),
                "hi", null, false, 30);
        String headerValue = req.headers().firstValue("x-goog-api-key").orElse("");
        assertTrue("a dirty (newline-containing) API key never reaches a header un-sanitized -- " +
                        "building the request doesn't throw AND the header value has no newline",
                !headerValue.contains("\n") && headerValue.equals("AIzaSyTestKey"));
    }

    /** Reads an HttpRequest's body back out for inspection -- BodyPublishers.ofString() is
     *  introspectable via its own Flow.Publisher, but the simplest reliable way to get the exact
     *  string back for a test is to capture it via a synchronous subscriber. */
    private static String bodyOf(HttpRequest req) {
        StringBuilder sb = new StringBuilder();
        req.bodyPublisher().get().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer item) { sb.append(java.nio.charset.StandardCharsets.UTF_8.decode(item)); }
            public void onError(Throwable throwable) { }
            public void onComplete() { }
        });
        return sb.toString();
    }
}
