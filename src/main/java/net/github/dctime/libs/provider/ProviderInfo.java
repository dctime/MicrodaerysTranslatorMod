package net.github.dctime.libs.provider;

import net.github.dctime.Config;

import java.util.List;

/**
 * Per-provider metadata: a human-readable name (as a lang KEY, not a {@code Component} -- see
 * below), which API shape it uses, whether an API key is needed, and a small curated list of model
 * presets. Deliberately NOT exhaustive (see {@code Config.MODEL_NAME}'s own comment -- model choice
 * is provider/account-specific); every provider's list always gets an implicit "Custom..." choice
 * appended by the screen itself, so an unlisted or player-typed model id is never lost. The first
 * entry in {@link #models()} is shown as the recommended default -- order matters, this is a
 * convention, not enforced by the type.
 * <p>
 * Lives in {@code libs/provider/}, not {@code screen/} (moved here per mailbox review round 017,
 * point O1's architecture note) -- {@link ModelPreset#supportsVision} now feeds directly into
 * {@link net.github.dctime.libs.Translator}'s request-building decision (see
 * {@link ProviderConfigResolver}/{@link ProviderSettings}), so a request-path class needing to
 * import GUI-package metadata would have been a backwards dependency. This class has zero
 * Minecraft/NeoForge dependency either way (see below), so the move costs nothing functionally --
 * it's a package-layering fix, not a technical requirement forced by the Minecraft-free constraint.
 * <p>
 * {@code displayNameKey} is a lang key (resolved to a {@code Component} only at the one GUI call
 * site that needs it, {@code TranslatorConfigScreen}'s Service CycleButton), not a
 * {@code net.minecraft.network.chat.Component} directly -- deliberately, per mailbox review
 * (round 016, point M1): a {@code Component} field would pull a Minecraft/NeoForge dependency into
 * this whole class the moment it's constructed (even {@code Component.literal(...)} touches
 * Minecraft classes), which is exactly what kept this class OUT of {@code tools/verify-*} despite
 * holding pure data (whether {@link Config.EndPoint} has an entry here, whether every entry's model
 * list is non-empty except CUSTOM, etc.) that doesn't need a running game to check. {@code
 * ModelPreset.displayName} stays a plain {@code String} (a brand/proper name, e.g. "Gemini 3.5
 * Flash Lite") for the same reason -- never translated, so it never needed to be a
 * {@code Component} in the first place; the GUI wraps it in {@code Component.literal(...)} where
 * it's actually rendered.
 * <p>
 * {@link Config.EndPoint#CUSTOM} has an EMPTY {@link #models()} list on purpose -- it has no
 * presets to curate at all, only a free-typed model field (see {@code TranslatorConfigScreen}'s
 * Custom Provider panel) -- and {@link #requiresApiKey()} is {@code false} for it too, since that
 * flag only controls whether the STANDARD API-key block renders; Custom Provider renders its own
 * dedicated key field inside its own panel instead.
 */
public record ProviderInfo(Config.EndPoint endpoint, String displayNameKey, ApiStyle apiStyle,
                            boolean requiresApiKey, List<ModelPreset> models) {

    /** Which adapter shape this provider uses -- informational for the GUI only; the actual
     *  dispatch lives in {@code ProviderAdapterRegistry}, not here (two sources of truth for the
     *  same fact is exactly what this refactor is trying to avoid, so this field is never read to
     *  make a networking decision, only to group providers sensibly in documentation/comments). */
    public enum ApiStyle {
        GEMINI_NATIVE, ANTHROPIC_NATIVE, OPENAI_COMPATIBLE, OLLAMA_LOCAL, CUSTOM
    }

    /**
     * @param preview Marks a model the provider itself currently labels preview/experimental --
     *                deliberately never given to the recommended (index 0) preset. NOTE (mailbox
     *                review round 017, point O2): this is currently a PRESET-ORDERING decision
     *                input only, not shown to the player anywhere -- there is no lang key for it
     *                and no widget reads it. That's a conscious choice (option (c) of the three
     *                O2 raised), not an oversight: displaying it would need a short, untranslated
     *                marker (same H5 lesson as the "★ " recommended prefix -- a translated
     *                "(Preview)" suffix would blow out button width the same way "(Recommended)"
     *                did), which is GUI work left for later rather than folded into an already
     *                large refactor. If this field is ever removed instead of surfaced, remove
     *                this whole javadoc paragraph with it.
     * @param supportsVision Image-input capability as documented by the provider. Consumed by
     *                {@link ProviderConfigResolver#resolve} to populate
     *                {@link ProviderSettings#supportsVision()}, which
     *                {@link net.github.dctime.libs.Translator} checks before ever attaching an
     *                image to a request -- see that class's request-building code for why this
     *                matters (a text-only model sent an image can 400 or silently ignore it,
     *                either way failing translation for exactly the line that carries it).
     */
    public record ModelPreset(String modelId, String displayName, boolean preview, boolean supportsVision) {
        public ModelPreset(String modelId, String displayName) {
            this(modelId, displayName, false, false);
        }
    }

    // Deliberately a hardcoded literal, not MicrodaerysTranslatorClient.MODID -- importing that
    // class would defeat the whole point of this class being Minecraft-free (see the class
    // javadoc): @Mod/@EventBusSubscriber-annotated classes pull in NeoForge at class-load time.
    // Verified identical to MicrodaerysTranslatorClient.MODID as of this writing; if that ever
    // changes, this is the one other place that also needs to.
    private static final String P = "microdaerystranslator.config.";

    // Provenance / confidence per entry (see the completion report for the full per-provider
    // breakdown): every model id below was checked against that provider's own live docs/model
    // catalog shortly before this list was written, not guessed from training data alone -- the
    // one partial exception is DeepSeek, whose primary docs site was unreachable from the research
    // sandbox and was instead cross-checked against three independent secondary mirrors.
    public static final List<ProviderInfo> ALL = List.of(
            new ProviderInfo(Config.EndPoint.GOOGLE_AI_STUDIO, P + "provider.google",
                    ApiStyle.GEMINI_NATIVE, true, List.of(
                    new ModelPreset("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", false, true),
                    new ModelPreset("gemini-3.6-flash", "Gemini 3.6 Flash", false, true),
                    new ModelPreset("gemini-3.7-flash", "Gemini 3.7 Flash", false, true)
            )),
            new ProviderInfo(Config.EndPoint.NVIDIA_NIM, P + "provider.nvidia",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("nvidia/nemotron-3-ultra-550b-a55b", "NVIDIA Nemotron 3 Ultra"),
                    new ModelPreset("nvidia/nemotron-3.5-lightning-30b-a3b", "NVIDIA Nemotron 3.5 Lightning", true, false)
            )),
            new ProviderInfo(Config.EndPoint.GROQ, P + "provider.groq",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("openai/gpt-oss-20b", "GPT OSS 20B"),
                    new ModelPreset("openai/gpt-oss-120b", "GPT OSS 120B"),
                    new ModelPreset("qwen/qwen3.8-27b", "Qwen 3.8 27B", true, false)
            )),
            new ProviderInfo(Config.EndPoint.OPENROUTER, P + "provider.openrouter",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("openrouter/free", "Free Models Router"),
                    new ModelPreset("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra (Free)")
            )),
            new ProviderInfo(Config.EndPoint.MISTRAL, P + "provider.mistral",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("mistral-small-latest", "Mistral Small"),
                    new ModelPreset("mistral-medium-latest", "Mistral Medium"),
                    new ModelPreset("mistral-large-latest", "Mistral Large")
            )),
            new ProviderInfo(Config.EndPoint.DEEPSEEK, P + "provider.deepseek",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("deepseek-v4-flash", "DeepSeek V4 Flash"),
                    new ModelPreset("deepseek-v4-pro", "DeepSeek V4 Pro"),
                    new ModelPreset("deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision", true, true)
            )),
            new ProviderInfo(Config.EndPoint.CEREBRAS, P + "provider.cerebras",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("gpt-oss-120b", "GPT OSS 120B"),
                    new ModelPreset("gemma-4-31b", "Gemma 4 31B", false, true)
            )),
            new ProviderInfo(Config.EndPoint.ANTHROPIC, P + "provider.anthropic",
                    ApiStyle.ANTHROPIC_NATIVE, true, List.of(
                    new ModelPreset("claude-sonnet-5", "Claude Sonnet 5", false, true),
                    new ModelPreset("claude-haiku-4-5-20251001", "Claude Haiku 4.5", false, true),
                    new ModelPreset("claude-opus-5", "Claude Opus 5", false, true)
            )),
            new ProviderInfo(Config.EndPoint.OPENAI, P + "provider.openai",
                    ApiStyle.OPENAI_COMPATIBLE, true, List.of(
                    new ModelPreset("gpt-5.6-luna", "GPT-5.6 Luna", false, true),
                    new ModelPreset("gpt-5.6-terra", "GPT-5.6 Terra", false, true),
                    new ModelPreset("gpt-5.6-sol", "GPT-5.6 Sol", false, true)
            )),
            new ProviderInfo(Config.EndPoint.OLLAMA, P + "provider.ollama",
                    ApiStyle.OLLAMA_LOCAL, false, List.of(
                    new ModelPreset("qwen3:4b", "Qwen3 4B"),
                    new ModelPreset("qwen3:8b", "Qwen3 8B"),
                    new ModelPreset("qwen3:14b", "Qwen3 14B"),
                    new ModelPreset("gemma3:4b", "Gemma 3 4B", false, true)
            )),
            new ProviderInfo(Config.EndPoint.CUSTOM, P + "provider.custom",
                    ApiStyle.CUSTOM, false, List.of())
    );

    /** Every {@link Config.EndPoint} has an entry in {@link #ALL} -- this is exhaustive by construction. */
    public static ProviderInfo of(Config.EndPoint endpoint) {
        for (ProviderInfo info : ALL) {
            if (info.endpoint() == endpoint) return info;
        }
        throw new IllegalArgumentException("No ProviderInfo registered for " + endpoint);
    }

    /**
     * Whether the given built-in provider's given model id is documented to accept image input.
     * Returns {@code true} (never blocks an image) when {@code modelId} doesn't match any of this
     * provider's curated presets -- i.e. the player typed their own Custom Model id. Deliberate
     * per mailbox review round 017, point O1: this mod has no basis to judge a player-supplied
     * model id's capability, and defaulting to "include the image" preserves the exact behavior
     * every provider had before this capability existed (never silently drop context the player
     * didn't ask to drop). Not meaningful for {@link Config.EndPoint#CUSTOM} -- that endpoint's
     * vision capability comes from {@code Config.CUSTOM_PROVIDER_SUPPORTS_VISION} instead, since
     * it has no preset list to look up (see {@link ProviderConfigResolver}).
     */
    public static boolean supportsVisionFor(Config.EndPoint endpoint, String modelId) {
        for (ModelPreset preset : of(endpoint).models()) {
            if (preset.modelId().equals(modelId)) return preset.supportsVision();
        }
        return true;
    }
}
