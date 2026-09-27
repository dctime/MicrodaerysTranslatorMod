package net.github.dctime.libs.provider;

import net.github.dctime.Config;

import java.util.EnumMap;
import java.util.Map;

/**
 * Single lookup from {@link Config.EndPoint} to its {@link TranslationProviderAdapter} -- the
 * thing that replaces the three independent per-provider dispatch points this refactor exists to
 * remove (see {@link TranslationProviderAdapter}'s javadoc). Because {@link Config.EndPoint} is a
 * closed enum and this map is built exhaustively at class-init time, a new enum constant added
 * here without a corresponding {@code map.put(...)} throws immediately (via
 * {@link #forEndpoint}) instead of silently misrouting to some other provider's adapter the way
 * the old {@code if/else} chains in {@code Translator} used to.
 * <p>
 * Base URLs/model-listing paths below are each independently verified against the provider's own
 * live documentation as of this writing (see the completion report for per-provider confidence
 * notes) -- none are guesses, but providers change endpoints over time; if one 404s in the future,
 * this is the one place to update it.
 */
public final class ProviderAdapterRegistry {

    private static final Map<Config.EndPoint, TranslationProviderAdapter> ADAPTERS = buildRegistry();

    private ProviderAdapterRegistry() {
    }

    private static Map<Config.EndPoint, TranslationProviderAdapter> buildRegistry() {
        Map<Config.EndPoint, TranslationProviderAdapter> map = new EnumMap<>(Config.EndPoint.class);

        map.put(Config.EndPoint.GOOGLE_AI_STUDIO, new GeminiAdapter());
        map.put(Config.EndPoint.OLLAMA, new OllamaAdapter());
        map.put(Config.EndPoint.ANTHROPIC, new AnthropicAdapter());

        map.put(Config.EndPoint.NVIDIA_NIM, new OpenAiCompatibleAdapter(Config.EndPoint.NVIDIA_NIM,
                new OpenAiCompatibleSpec("https://integrate.api.nvidia.com/v1", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));
        map.put(Config.EndPoint.GROQ, new OpenAiCompatibleAdapter(Config.EndPoint.GROQ,
                new OpenAiCompatibleSpec("https://api.groq.com/openai/v1", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));
        map.put(Config.EndPoint.OPENROUTER, new OpenAiCompatibleAdapter(Config.EndPoint.OPENROUTER,
                new OpenAiCompatibleSpec("https://openrouter.ai/api/v1", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));
        map.put(Config.EndPoint.MISTRAL, new OpenAiCompatibleAdapter(Config.EndPoint.MISTRAL,
                new OpenAiCompatibleSpec("https://api.mistral.ai/v1", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));
        map.put(Config.EndPoint.DEEPSEEK, new OpenAiCompatibleAdapter(Config.EndPoint.DEEPSEEK,
                new OpenAiCompatibleSpec("https://api.deepseek.com", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));
        map.put(Config.EndPoint.CEREBRAS, new OpenAiCompatibleAdapter(Config.EndPoint.CEREBRAS,
                new OpenAiCompatibleSpec("https://api.cerebras.ai/v1", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));
        map.put(Config.EndPoint.OPENAI, new OpenAiCompatibleAdapter(Config.EndPoint.OPENAI,
                new OpenAiCompatibleSpec("https://api.openai.com/v1", "/chat/completions",
                        "/models", AuthMode.BEARER, Map.of()),
                true));

        // Custom Provider: no static spec -- resolved fresh from ProviderSettings on every call
        // (see OpenAiCompatibleAdapter.resolveSpec), since base URL/auth mode are player-typed.
        map.put(Config.EndPoint.CUSTOM, new OpenAiCompatibleAdapter(Config.EndPoint.CUSTOM, null, true));

        return Map.copyOf(map);
    }

    public static TranslationProviderAdapter forEndpoint(Config.EndPoint endpoint) {
        TranslationProviderAdapter adapter = ADAPTERS.get(endpoint);
        if (adapter == null) {
            throw new IllegalStateException("No TranslationProviderAdapter registered for " + endpoint);
        }
        return adapter;
    }
}
