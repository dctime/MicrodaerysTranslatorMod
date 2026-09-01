package net.github.dctime.screen;

import net.github.dctime.Config;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * UI-only display metadata for each {@link Config.EndPoint}: a human-readable name, whether an
 * API key is needed, and a small curated list of model presets. Deliberately NOT exhaustive (see
 * {@code Config.MODEL_NAME}'s own comment -- model choice is provider/account-specific); every
 * provider's list always gets an implicit "Custom..." choice appended by the screen itself, so an
 * unlisted or player-typed model id is never lost. The first entry in {@link #models()} is shown
 * as the recommended default -- order matters, this is a convention, not enforced by the type.
 */
public record ProviderInfo(Config.EndPoint endpoint, Component displayName, boolean requiresApiKey, List<ModelPreset> models) {

    public record ModelPreset(String modelId, Component displayName) {}

    // Provenance of each model id (see mailbox review #002 point E4 -- a wrong id here 404s a
    // brand-new player's very first attempt, with Custom as the only escape hatch, so this isn't
    // decorative): NOT independently verified against a live list-models call or each provider's
    // current docs. Confidence varies per entry:
    //   - mistral-small-latest: the HIGHEST-confidence entry -- it's Config.MODEL_NAME's own
    //     existing shipped default (see Config.java), i.e. this project already trusts it.
    //   - gemma-4-4b-it: given directly by the human requester (they asked for Gemma 4 instead of
    //     Gemma 3 by name), not this mod's guess -- same provenance class as gemini-3.1-flash-lite.
    //   - gemini-3.1-flash-lite: came verbatim from the human requester's own spec for this GUI
    //     (they named this exact model as their desired recommended default), not this mod's guess.
    //   - gemini-2.5-flash, mistral-large-latest: this author's own training-data knowledge of
    //     each provider's naming convention, medium-high confidence, not verified live.
    //   - llama3.2, qwen2.5: well-known Ollama model family names, but Ollama's exact registry tag
    //     format is NOT verified -- and moot for most players anyway, since Ollama's whole point is
    //     "whatever the player already pulled locally", which these presets can't know either way.
    // None of these are the ONLY path forward if wrong: Custom is always available, and (since
    // mailbox review #002 point E2) can no longer be silently submitted blank.
    public static final List<ProviderInfo> ALL = List.of(
            new ProviderInfo(Config.EndPoint.GOOGLE_AI_STUDIO, Component.literal("Google AI Studio"), true, List.of(
                    new ModelPreset("gemini-3.1-flash-lite", Component.literal("Gemini 3.1 Flash Lite")),
                    new ModelPreset("gemini-2.5-flash", Component.literal("Gemini 2.5 Flash")),
                    new ModelPreset("gemma-4-4b-it", Component.literal("Gemma 4 4B"))
            )),
            new ProviderInfo(Config.EndPoint.MISTRAL, Component.literal("Mistral AI"), true, List.of(
                    new ModelPreset("mistral-small-latest", Component.literal("Mistral Small")),
                    new ModelPreset("mistral-large-latest", Component.literal("Mistral Large"))
            )),
            new ProviderInfo(Config.EndPoint.OLLAMA, Component.literal("Ollama (Local)"), false, List.of(
                    new ModelPreset("llama3.2", Component.literal("Llama 3.2")),
                    new ModelPreset("qwen2.5", Component.literal("Qwen 2.5"))
            ))
    );

    /** Every {@link Config.EndPoint} has an entry in {@link #ALL} -- this is exhaustive by construction. */
    public static ProviderInfo of(Config.EndPoint endpoint) {
        for (ProviderInfo info : ALL) {
            if (info.endpoint() == endpoint) return info;
        }
        throw new IllegalArgumentException("No ProviderInfo registered for " + endpoint);
    }
}
