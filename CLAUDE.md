# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Microdaery's Translator — a **client-only** NeoForge mod for Minecraft 1.21.1 (`@Mod(dist = Dist.CLIENT)`)
that sends in-game English text to an AI endpoint (Google AI Studio / Mistral / local Ollama) and renders
the translation back into tooltips, Jade overlays, advancements and FTB Quests.

## Commands

```bash
./gradlew build                  # compile + jar (CI runs exactly this, JDK 21)
./gradlew runClient              # launch a dev client (with JEI/Jade/FTB Quests/Better Advancements)
./gradlew runData                # datagen -> src/generated/resources (see LanguageProvider)
./gradlew --refresh-dependencies # when Curse/Modrinth deps fail to resolve
```

Java toolchain is **21** (Mojang ships 21 for 1.21.1) — do not raise it.
`runData` may need the FTB `runtimeOnly` lines in `build.gradle` commented out (noted there).

### Tests

There is no JUnit/Gradle test suite. Regression checks live as standalone `main()` programs under
`tools/verify-*/`, each run against the real compiled production classes:

```bash
./gradlew build
MAIN_CLASSES=build/classes/java/main
GSON=$(find ~/.gradle -name 'gson-2.10.1.jar' | head -1)   # only for disk-cache / json-escaping
javac -cp "$GSON:$MAIN_CLASSES" -d tools/verify-rate-limiter tools/verify-rate-limiter/VerifyRateLimiter.java
java  -cp "tools/verify-rate-limiter:$GSON:$MAIN_CLASSES" VerifyRateLimiter
```

Each file's header comment carries its exact run lines **and an explicit statement of what it does
NOT verify** — keep that honesty when adding one. This only works for classes with no Minecraft
dependency; loading e.g. `Translator` headless throws `NoClassDefFoundError` from its `<clinit>`.

## Architecture

### Translator is the hub
`libs/Translator.java` owns everything about a translation: cache, official-translation
short-circuits, endpoint selection, concurrency, rate limiting, error reporting. Every UI surface
calls into it; none of them talk to an API directly.

- **Cache key is `record CacheKey(String lang, String text)`** — never a raw string, so switching
  target language can't serve a stale translation. Always go through `keyFor(text)`.
- **`resolveTargetLanguage()` is the single source of truth** for "what language are we translating
  into" (`FOLLOW_GAME_LANGUAGE` ? game UI language : `Config.TARGET_LANGUAGE`). Never read
  `Config.TARGET_LANGUAGE.get()` at a call site — cache key, prompt, skip-detection and official
  lookup must not drift apart.
- **Throttling is two independent layers**: `Semaphore CONCURRENCY_LIMIT` (4 in flight at once) and
  `RateLimiter REQUEST_RATE_LIMITER` (sliding 60s window, limit read fresh from
  `MAX_REQUESTS_PER_MINUTE` each call). Plus `IN_FLIGHT` dedup per text and `RETRY_AFTER`/
  `RETRY_ATTEMPTS` exponential backoff on HTTP 429. A request that loses `tryAcquire()` is **dropped,
  not queued** — the design assumes the caller fires again on a later frame/tick.
- **Disk cache**: `translationCache` is flattened to `config/microdaerystranslator/translation_cache.json`
  only when `cacheDirty` is set, flushed every 600 ticks (`OnClientTickEvent`) and on logout. Any new
  write to the cache must set `cacheDirty = true`.

### Official-translation short-circuit
Before ever calling the AI, the mod tries to reuse translations that vanilla/mods already ship
(`libs/OfficialTranslationLookup`, backed by `ClientLanguage.loadFrom` per language, cache
invalidated on resource reload). Eligibility requires the currently rendered text to match the
official value **in the game's current display language**, proving it's the default name and not a
rename. Chain lives in **`Translator.resolveOrRequestTranslation(stack, text, isFirstLine)`**:
item name (line 0) → enchantment line → known flat header keys (`KNOWN_FLAT_TOOLTIP_KEYS`) →
attribute-modifier value line → AI request.

**Add new short-circuits inside `resolveOrRequestTranslation`, not at a call site.** Both tooltip
systems (vanilla `RenderTooltipEvent` and Jade `TestTooltipCollectedCallback`) route through it
precisely because the Jade path had silently fallen behind (#20).

### Entry points (how text reaches Translator)
| Surface | Mechanism |
|---|---|
| Inventory tooltips | `events/RenderTooltipEvent` (`GatherComponents`) |
| Container pre-translation | `Translator.pretranslateOpenContainerIfAny()` every client tick |
| Jade overlay | `compability/jade/WailaPlugin` → `TestTooltipCollectedCallback` |
| Vanilla advancements | `mixin/AdvancementWidgetMixin` |
| Better Advancements | `mixin/BetterAdvancementWidgetMixin` |
| FTB Quests | `mixin/ftbquests/*` + accessor interfaces in `libs/ftbquests/` |
| Screenshot OCR | `events/ScreenShotEvent` → `screen/ScreenShotSelectAreaScreen` → `libs/ScreenShotter` |

All mixins are gated at load time by `compability/LoadMixinPlugin.shouldApplyMixin` (checks
`LoadingModList` for `ftbquests` / `betteradvancements`); a new mixin must be added both to
`microdaerystranslator.mixins.json` **and** to that method, or it will never apply.

### Minecraft-free `libs/` classes
`JsonUtil`, `RetryPolicy`, `RateLimiter`, `TargetLanguage`, `PromptTemplates`,
`WelcomeMessageTemplates`, `TranslationDiskCache` deliberately have **no Minecraft/NeoForge import**,
which is the only reason `tools/verify-*` can exercise them headless. Keep new pure logic there
rather than inlining it into `Translator` or a mixin.

### Language handling
Language codes are Minecraft's own (`zh_tw`, `zh_cn`, `ja_jp`, `en_us`, `es_es`, `fr_fr`) everywhere —
one code universe, no translation layer. `TargetLanguage` and `PromptTemplates` are closed curated
tables; an unknown code degrades gracefully (raw code as display name, never "already translated",
generic English prompt) instead of throwing. Prompts are written **natively per language**, not one
template with the language name substituted.

### Config
`Config.java` builds a single client `ModConfigSpec` → `config/microdaerystranslator-client.toml`.
NeoForge never rewrites an existing key when the code default changes, so
`PromptTemplates.isBlankOrLegacyDefault(...)` recognizes prompt defaults shipped by past versions and
treats them as blank — extend those legacy sets rather than expecting players to clear the field.
Every new config key and keybind needs a display name added to `datagen/LanguageProvider` and a
`./gradlew runData` run.

## Conventions

- Comments explain **why**, often citing the issue number that motivated the code (`#16`, `#20`) and
  the failure mode that would return if it were removed. Match this when touching that code.
- Commit subjects are mostly Traditional Chinese with a trailing issue number, e.g.
  `容器 GUI 開啟時預翻裡面的物品（#16）`.
- User-facing chat messages are sent in English and Chinese as two lines (`Translator.showMessage`).
- `[DIAG]` `LOGGER.info` lines are deliberate temporary diagnostics for the "translation comes back
  in English" reports — don't silently downgrade them to `debug` (NeoForge hides `debug` by default).
