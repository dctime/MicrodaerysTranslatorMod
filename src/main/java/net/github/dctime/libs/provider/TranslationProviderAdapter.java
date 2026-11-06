package net.github.dctime.libs.provider;

import net.github.dctime.Config;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;

/**
 * One implementation per API *shape*, not per provider -- {@link OpenAiCompatibleAdapter} alone
 * serves 8 of the 11 {@link Config.EndPoint} values. This is the single lookup
 * {@link net.github.dctime.libs.Translator} and
 * {@link net.github.dctime.libs.TranslationConnectionTester} both go through, replacing what used
 * to be three independent, non-synchronized per-provider dispatch points (two {@code if/else}
 * chains in {@code Translator} -- both with a silent Google fallback for any unmatched value -- and
 * a separate exhaustive {@code switch} in the connection tester). See
 * {@link ProviderAdapterRegistry}.
 * <p>
 * Response parsing is dispatched by WHICH adapter built the request (captured in the async
 * callback closure at request time), never by sniffing the response body's shape -- the old
 * {@code isOllamaResponse}/{@code isMistralResponse} heuristics are gone; several of the new
 * OpenAI-compatible-shaped providers share the exact same {@code choices[0].message.content}
 * response shape Mistral already had, so shape-sniffing would have become ambiguous the moment a
 * second OpenAI-compatible provider existed.
 * <p>
 * {@code parseTranslationResponse} may throw any unchecked exception on malformed input (matching
 * the pre-existing behavior of {@code Translator.parseGeminiResponse}/etc.) -- the caller already
 * wraps every parse call in a generic {@code catch (Exception e)}.
 */
public interface TranslationProviderAdapter {

    Config.EndPoint type();

    /** True if a model-list GET is expected to work for this provider -- if false, Test Connection
     *  falls back to a minimal ("Reply with OK only.") generation request instead. Every adapter
     *  in this codebase currently returns true (see the completion report for which of those are
     *  independently confirmed vs. assumed-by-OpenAI-compatible-convention); the false path exists
     *  for a future provider that turns out not to expose one, not because one is used today. */
    boolean supportsModelListing();

    HttpRequest buildTranslationRequest(ProviderSettings settings, String prompt,
                                         @Nullable String imageBase64, boolean isScreenshot,
                                         int timeoutSeconds);

    String parseTranslationResponse(String responseBody);

    /** Model-list GET (when {@link #supportsModelListing()}) or a minimal generation request
     *  otherwise -- callers don't need to branch on which; each adapter builds whichever request
     *  actually answers "is this connection/auth/model working" for itself. */
    HttpRequest buildConnectionTestRequest(ProviderSettings settings);

    /** Best-effort match against {@link #buildConnectionTestRequest}'s response; false on any parse
     *  failure or shape mismatch (never throws) -- a miss is a warning, never a hard failure, since
     *  list-model responses can be paginated or tagged in ways a typed model id doesn't match. */
    boolean modelAppearsInConnectionTestResponse(String responseBody, String model);
}
