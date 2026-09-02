package net.github.dctime.libs;

import com.mojang.blaze3d.systems.RenderSystem;
import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.events.ScreenEventRender;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderAdapterRegistry;
import net.github.dctime.libs.provider.ProviderConfigResolver;
import net.github.dctime.libs.provider.ProviderSettings;
import net.github.dctime.libs.provider.TranslationProviderAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static net.github.dctime.libs.ScreenShotter.getItemStackImage;

public class Translator {
    // language is part of the key so switching Config.TARGET_LANGUAGE doesn't serve a cached
    // translation from a previous target language. A record has built-in equals/hashCode, so
    // there's no string-concatenation-with-a-delimiter to accidentally collide with real content.
    private record CacheKey(String lang, String text) {}

    // single source of truth for "what language are we actually translating into right now" --
    // every call site (cache key, prompt, skip-detection, official-translation lookup) must go
    // through this, not read Config.TARGET_LANGUAGE.get() directly, or they can silently drift
    // out of sync with each other (same failure shape as the fixedText/originalText coupling).
    private static String resolveTargetLanguage() {
        return Config.FOLLOW_GAME_LANGUAGE.get()
                ? Minecraft.getInstance().getLanguageManager().getSelected()
                : Config.TARGET_LANGUAGE.get();
    }

    private static CacheKey keyFor(String text) {
        return new CacheKey(resolveTargetLanguage(), text);
    }

    private static ConcurrentHashMap<CacheKey, String> translationCache = new ConcurrentHashMap<>();

    // set (O(1)) whenever translationCache changes; a periodic tick (see OnClientTickEvent)
    // flushes to disk only when this is true, instead of re-serializing the whole cache on every
    // single successful translation (which would cost O(cache size) per translation).
    private static volatile boolean cacheDirty = false;

    private static Path cacheFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(MicrodaerysTranslatorClient.MODID).resolve("translation_cache.json");
    }

    /** Call once at mod startup, before any translation happens. */
    public static void loadCacheFromDisk() {
        Map<String, Map<String, String>> nested = TranslationDiskCache.load(cacheFilePath());
        for (Map.Entry<String, Map<String, String>> langEntry : nested.entrySet()) {
            for (Map.Entry<String, String> textEntry : langEntry.getValue().entrySet()) {
                translationCache.put(new CacheKey(langEntry.getKey(), textEntry.getKey()), textEntry.getValue());
            }
        }
    }

    // Every disk write goes through this single-thread executor, never ForkJoinPool.commonPool
    // directly, so two overlapping flushes (the periodic tick's and, since clearCache() started
    // flushing on demand, an on-demand one) can never run their save() calls concurrently against
    // each other -- they queue instead. TranslationDiskCache.save() already gives each individual
    // call a uniquely-named tmp file (so a race can't corrupt the file into a mixed/invalid blob),
    // but it makes no promise about WHICH concurrent call's content ends up on disk if two run at
    // once (see its javadoc) -- this executor is what turns "call order" into "disk order" for
    // this mod's one real caller. Daemon thread: matches the previous commonPool-based behavior
    // (doesn't block JVM shutdown by itself); see flushCacheToDiskSync() for the path that
    // deliberately waits for a write to finish before returning.
    private static final ExecutorService CACHE_WRITE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "microdaerystranslator-cache-writer");
        t.setDaemon(true);
        return t;
    });

    /** Cheap to call often (e.g. every tick): no-ops unless the cache actually changed since the last flush. */
    public static void flushCacheToDiskIfDirty() {
        if (!cacheDirty) return;
        cacheDirty = false;
        CACHE_WRITE_EXECUTOR.execute(Translator::writeCacheToDisk);
    }

    /**
     * Same as {@link #flushCacheToDiskIfDirty()}, but BLOCKS the calling thread until the write
     * has actually finished (or {@link #CACHE_WRITE_TIMEOUT_SECONDS} elapses), instead of merely
     * queuing it. Used by {@link #clearCache}: that path can be followed immediately by the player
     * quitting the game, and the periodic/logout flushes it would otherwise rely on don't fire
     * reliably from the main menu (no world tick, no logout event) -- queuing the write (as
     * flushCacheToDiskIfDirty() does) only narrows that race to milliseconds instead of closing
     * it, since the write still happens on a daemon thread the JVM can kill mid-task on exit.
     * <p>
     * Waiting here is USUALLY cheap -- not because this task's own payload is small (it is, right
     * after clear() empties the map, but that's not the point), but because {@code
     * CACHE_WRITE_EXECUTOR} is single-threaded: if a periodic flush of a large cache is already
     * running (or merely queued ahead of this call), this blocks until THAT finishes too, not just
     * its own near-instant write. Bounded by a timeout specifically so that worst case (a slow
     * disk, an antivirus lock, a network-mounted config dir) freezes the render thread for at most
     * {@link #CACHE_WRITE_TIMEOUT_SECONDS} seconds, never indefinitely -- a timeout does NOT
     * cancel the underlying task, which keeps running in the background and will still land
     * eventually.
     */
    private static final int CACHE_WRITE_TIMEOUT_SECONDS = 5;

    private static void flushCacheToDiskSync() {
        if (!cacheDirty) return;
        cacheDirty = false;
        try {
            CACHE_WRITE_EXECUTOR.submit(Translator::writeCacheToDisk).get(CACHE_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cacheDirty = true; // unknown whether the write landed -- assume not, let a later flush retry
        } catch (ExecutionException e) {
            LOGGER.warn("Failed to flush translation cache to disk: " + e.getMessage());
            cacheDirty = true; // the write did not land -- don't let the caller report success while this stays false
        } catch (TimeoutException e) {
            LOGGER.warn("Translation cache write did not finish within " + CACHE_WRITE_TIMEOUT_SECONDS
                    + "s; continuing in the background instead of freezing the game.");
            cacheDirty = true; // don't know yet whether the still-running task will succeed -- a later flush retries
        }
    }

    private static void writeCacheToDisk() {
        // ConcurrentHashMap iteration is thread-safe on its own, so the O(cache size) flatten can
        // happen on the writer thread too -- the caller only pays O(1): clear the flag, hand off.
        Map<String, Map<String, String>> nested = new HashMap<>();
        for (Map.Entry<CacheKey, String> entry : translationCache.entrySet()) {
            nested.computeIfAbsent(entry.getKey().lang(), lang -> new HashMap<>()).put(entry.getKey().text(), entry.getValue());
        }
        try {
            TranslationDiskCache.save(cacheFilePath(), nested);
        } catch (IOException e) {
            LOGGER.warn("Failed to write translation cache to disk: " + e.getMessage());
            // Without this, a failed write leaves cacheDirty permanently false (both callers set
            // it false BEFORE this task runs, to avoid a re-check race), so the periodic flush
            // would never retry -- the failed write is lost until some unrelated new translation
            // happens to mark the cache dirty again. Safe to set unconditionally: if something
            // else already set it true in the meantime, this is a harmless redundant write later,
            // never a lost one.
            cacheDirty = true;
        }
    }

    // per-text in-flight tracking replaces the old single global "translating" lock, which
    // dropped every request but the first when hovering across several items in one frame.
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    // 429 (RPM) backoff bookkeeping, keyed by the same text as IN_FLIGHT/translationCache.
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RETRY_ATTEMPTS = new ConcurrentHashMap<>();
    // caps how many translation requests can be in flight at once across ALL texts, so sweeping
    // the mouse over a long row of items doesn't fire off unbounded concurrent requests.
    private static final Semaphore CONCURRENCY_LIMIT = new Semaphore(4);
    // CONCURRENCY_LIMIT alone doesn't stop a burst rate over time -- as each of those 4 slots
    // finishes it immediately gets reused, e.g. while pretranslateOpenContainerIfAny() (#16) works
    // through a container full of different uncached items every tick, which can add up to far
    // more than a free API tier's requests-PER-MINUTE quota even though only 4 are ever truly
    // concurrent. This is a real sliding-window throttle on top of the concurrency cap, not a
    // duplicate of it. Config.MAX_REQUESTS_PER_MINUTE is read fresh on every check (not captured
    // once here) so changing it in the config screen takes effect immediately.
    private static final RateLimiter REQUEST_RATE_LIMITER = new RateLimiter(60_000L);
    // screenshot translation is a single, unrelated flow (fixed ":" text) with its own busy flag.
    public static volatile boolean screenshotTranslating = false;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static boolean isTranslating() {
        return screenshotTranslating || !IN_FLIGHT.isEmpty();
    }
    private static boolean hasShowConnectionError = false;
    private static boolean hasShowAPIKEYError = false;
    private static boolean hasShowRequestTooFrequentError = false;
    private static boolean hasShowOtherError = false;
    private static Logger LOGGER = LoggerFactory.getLogger(Translator.class);
    // --- ftb quest ---

    public enum KeyTriggeredSource {
        MOUSE_BUTTON_EVENT,
        CLIENT_TICK
    }

    private static boolean deletingTranslationKeyHold = false;
    private static KeyTriggeredSource deletingTranslationSource = null;

    public static boolean getDeletingTranslationKeyHold() {
        return deletingTranslationKeyHold;
    }

    public static void setDeletingTranslationKeyHold(boolean value, KeyTriggeredSource src) {
        if (!value && src != deletingTranslationSource) return; // two sources spamming false
        if (!deletingTranslationKeyHold && value) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            player.sendSystemMessage(Component.literal("Cleared Displayed Translations").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal("重新翻譯目前顯示的翻譯").withStyle(ChatFormatting.YELLOW));
        }
        deletingTranslationSource = src;
        deletingTranslationKeyHold = value;
    }

    public static final Style translatedStyle = Style.EMPTY.withColor(ChatFormatting.GRAY);

    public static boolean textInCache(String text) {
        if (deletingTranslationKeyHold) {
            translationCache.remove(keyFor(text));
//            System.out.println("REMOVE TRANSLATION: " + text);
            return false;
        }

//        System.out.println("FINDING TRNASLATION IN CACHE: " + text + " deleting: " + deletingTranslationKeyHold);
        return translationCache.containsKey(keyFor(text));
    }

    public static String getTranslationFromCache(String text) {
        return translationCache.get(keyFor(text));
    }

    /** How many entries are currently cached, across all target languages. Cheap (O(1)): backed by ConcurrentHashMap.size(). */
    public static int getCacheSize() {
        return translationCache.size();
    }

    /**
     * Clears the cache regardless of whether a player is present (e.g. the config screen can be
     * opened from the main menu, before joining a world, or {@code DELETE_TRANSLATION_CACHE} can
     * be pressed at the title screen -- {@code KeyConflictContext.UNIVERSAL} fires everywhere).
     * {@code showMessage} only gates the chat feedback -- the clear itself, and {@code
     * cacheDirty}, are never skipped just because there's no player to message. NOTE: this is a
     * real behavior change from the old player-gated clearCache(): previously a no-player call was
     * a full no-op (cache NOT cleared); now it always clears, only the chat message is
     * conditional. Callers with no other feedback mechanism (a GUI updating its own "N entries"
     * label, for instance) should pass false and surface the result themselves.
     * <p>
     * Flushes to disk SYNCHRONOUSLY (blocks until the write finishes) rather than going through
     * the periodic tick/logout flush: neither of those fires reliably from the main menu (no world
     * tick, no logout event), and merely queuing an async write (as flushCacheToDiskIfDirty() does
     * elsewhere) leaves a real, if narrow, window where a player who quits within milliseconds of
     * clearing can still lose the clear -- the write runs on a daemon thread the JVM can kill
     * mid-task on exit. Blocking here is cheap: by this point clear() has already emptied the map,
     * so the write is a handful of bytes, not O(cache size).
     */
    public static void clearCache(boolean showMessage) {
        if (Translator.translationCache.isEmpty()) return;
        Translator.translationCache.clear();
        cacheDirty = true; // otherwise a quit before the flush below leaves the stale cache on disk
        flushCacheToDiskSync();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            if (showMessage) {
                player.sendSystemMessage(Component.literal("Translation cache cleared.").withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("清除翻譯快取").withStyle(ChatFormatting.YELLOW));
            }
        } else if (showMessage) {
            // showMessage=true means the caller wanted chat feedback but there was no player to
            // give it to -- log is the only feedback channel left for that case. showMessage=false
            // callers (e.g. the GUI) have their own feedback (a widget label), so no log line here
            // for them -- there's nothing unusual to record.
            LOGGER.info("Translation cache cleared (no player present to notify in chat).");
        }
    }

    public static void clearCache() {
        clearCache(true);
    }

    /**
     * The active provider's live (api_key, model, plus Custom Provider's base URL/auth mode),
     * resolved through {@link ProviderConfigResolver} -- the single source of truth for this that
     * {@code PendingTranslatorConfig.loadFromConfig()} also uses, so a legacy-config player's first
     * translation request after upgrading (before ever opening the config screen) still resolves
     * to their pre-existing api_key/model instead of a blank per-provider field.
     */
    private static ProviderSettings resolveActiveProviderSettings(Config.EndPoint endpoint) {
        ProviderConfigResolver.ResolvedProviderConfig resolved = ProviderConfigResolver.resolve(endpoint);
        if (endpoint != Config.EndPoint.CUSTOM) {
            return new ProviderSettings(endpoint, resolved.apiKey(), resolved.model(), null, null, resolved.supportsVision());
        }
        AuthMode authMode = "NONE".equalsIgnoreCase(Config.CUSTOM_PROVIDER_AUTH_MODE.get())
                ? AuthMode.NONE : AuthMode.BEARER;
        return new ProviderSettings(endpoint, resolved.apiKey(), resolved.model(),
                Config.CUSTOM_PROVIDER_BASE_URL.get(), authMode, resolved.supportsVision());
    }

    private static HttpRequest buildRequest(TranslationProviderAdapter adapter, ProviderSettings settings,
                                             String fixedText, @Nullable String image, boolean isScreenShot,
                                             Config.EndPoint endpoint) {
        String prompt = isScreenShot ? resolvePrompt(true) : resolvePrompt(false) + "\n" + fixedText;
        HttpRequest request = adapter.buildTranslationRequest(settings, prompt, image, isScreenShot,
                Config.TIMEOUT_DURATION_CONFIG.get());
        // temporary diagnostic logging, see the [DIAG] log in handleHttpResponse
        LOGGER.info("[DIAG] translation request: endpoint=" + endpoint + " model=" + settings.model()
                + " url=" + request.uri() + " hasImage=" + (image != null) + " prompt=[" + prompt + "]");
        return request;
    }

    private static String resolvePrompt(boolean isScreenShot) {
        String override = isScreenShot ? Config.PROMPT_SCREENSHOT.get() : Config.PROMPT.get();
        String targetLanguage = resolveTargetLanguage();

        // blank OR byte-identical to a default this mod shipped in some past version (NeoForge
        // never overwrites an existing config key's saved value when the code's default changes,
        // so a config generated under an older version is permanently stuck on one of those exact
        // strings otherwise -- confirmed with a real player's config, not hypothetical) -> pick
        // the built-in prompt written natively for the target language (see PromptTemplates); any
        // other non-blank value is a genuine player override, used for every language.
        boolean useBuiltInTemplate = isScreenShot
                ? PromptTemplates.isBlankOrLegacyScreenshotDefault(override)
                : PromptTemplates.isBlankOrLegacyDefault(override);
        if (useBuiltInTemplate) {
            return isScreenShot ? PromptTemplates.screenshotPromptFor(targetLanguage) : PromptTemplates.promptFor(targetLanguage);
        }

        // PROMPT/PROMPT_SCREENSHOT overrides are freely player-editable config strings, so this
        // must be a literal substitution, not String.format()/.formatted() semantics: a lone '%'
        // typed into a custom prompt (e.g. "不要翻超過 90% 的內容") would make .formatted() throw
        // IllegalFormatException, which nothing upstream catches -- a config edit that once was
        // harmless plain text would crash every tooltip render. .replace() can never throw.
        return override.replace("%s", TargetLanguage.displayName(targetLanguage));
    }

    public static void requestTranslateToTraditionalChinese(String textInEnglish) throws IOException, InterruptedException {
        requestTranslateToTraditionalChinese(textInEnglish, null, false);
    }

    /**
     * True when this tooltip line is just "which mod is this item from" (e.g. JEI's mod-name
     * line: "Minecraft", "FTB Quests"). That text is a mod's own brand name -- a proper noun the
     * AI already declines to translate per the prompt's own "don't translate mod IDs" rule -- so
     * requesting a translation for it only wastes an API call and, once the AI dutifully echoes
     * it back unchanged, appends a confusing "Minecraft Minecraft" duplicate onto the tooltip.
     * The caller should leave a matching line completely untouched: no lookup, no AI, no append.
     */
    public static boolean isModNameLine(ItemStack stack, String renderedText) {
        if (stack == null || stack.isEmpty() || renderedText == null || renderedText.isBlank()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ModList.get().getModContainerById(itemId.getNamespace())
                .map(container -> container.getModInfo().getDisplayName().equals(renderedText))
                .orElse(false);
    }

    /**
     * Called every tick (see OnClientTickEvent) while a container screen (chest, crafting table,
     * ...) is open, so items inside it are already cached by the time the player hovers them.
     * Deliberately NOT a one-shot call on screen-open: the whole IN_FLIGHT/Semaphore(4) safety
     * net only stays safe because a request that loses tryAcquire() gets retried on a LATER call
     * -- true every frame for hover (RenderTooltipEvent), but a single screen-open event never
     * fires again, so most of a large container's items would silently never get retried. Calling
     * this every tick while the screen stays open reuses that same "there will be a next attempt"
     * guarantee instead of only pretending to.
     * Tries the official-translation short-circuit first (same as tooltip line 0) before ever
     * calling the AI, so a container full of ordinary vanilla items costs zero API calls.
     */
    public static void pretranslateOpenContainerIfAny() {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        for (Slot slot : containerScreen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String renderedText = stack.getHoverName().getString();
            if (textInCache(renderedText)) continue;
            if (tryOfficialTranslationForItemName(stack, renderedText)) continue;

            try {
                requestTranslateItemStackToTraditionalChinese(renderedText, stack);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("Failed to pretranslate container item: " + e.getMessage());
            }
        }
    }

    /**
     * Item/block display-name short-circuit: if the item's own registry translation key already
     * has an official translation for the current target language, use it directly instead of
     * calling the AI. Scoped ONLY to the item's default name (tooltip line 0) -- ordinary lore
     * text has no stable translation key at all, so this can't extend to lore lines; those keep
     * going through the AI path unchanged. Returns true (and populates the cache) on a hit.
     */
    public static boolean tryOfficialTranslationForItemName(ItemStack stack, String renderedText) {
        if (stack == null || stack.isEmpty()) return false;

        // the stack-aware overload, NOT the bare no-arg getDescriptionId(): vanilla's own
        // ItemStack.getHoverName() -> getDescriptionId(this) uses the stack-aware key, and some
        // items override ONLY that overload to compute a stack-dependent key (e.g. TippedArrowItem
        // returns a different key per potion effect -- "Arrow of Healing" vs "Arrow of Harming" is
        // NOT the same translation key). The no-arg version silently returns the wrong, generic
        // key for those items, so the lookup key here must match what's actually rendered.
        String key = stack.getItem().getDescriptionId(stack);
        // the safety check inside lookup() must compare against whatever language the game is
        // ACTUALLY showing right now, not always English -- a Chinese-UI client renders "羊毛",
        // not "Wool", so that's what has to match zh_tw's official value, not en_us's.
        String currentGameLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String officialTranslation = OfficialTranslationLookup.lookup(key, currentGameLanguage, resolveTargetLanguage(), renderedText);
        if (officialTranslation == null) return false;

        translationCache.put(keyFor(renderedText), officialTranslation);
        cacheDirty = true;
        LOGGER.debug("Used official translation for " + key + ": " + renderedText + " -> " + officialTranslation);
        return true;
    }

    /**
     * Same short-circuit idea as {@link #tryOfficialTranslationForItemName}, but for an
     * enchantment tooltip line ("Sharpness V"). Scoped to enchantments specifically -- NOT
     * potion/status-effect lines, which embed a live remaining-duration string that's never the
     * same text twice, so there's nothing stable to match against at all.
     */
    public static boolean tryOfficialTranslationForEnchantmentLine(ItemStack stack, String renderedText) {
        if (stack == null || stack.isEmpty() || !stack.isEnchanted()) return false;

        String currentGameLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String officialTranslation = OfficialTranslationLookup.lookupEnchantmentLine(stack, currentGameLanguage, resolveTargetLanguage(), renderedText);
        if (officialTranslation == null) return false;

        translationCache.put(keyFor(renderedText), officialTranslation);
        cacheDirty = true;
        LOGGER.debug("Used official enchantment translation: " + renderedText + " -> " + officialTranslation);
        return true;
    }

    // Argument-free ("flat") vanilla translation keys that can appear as a standalone tooltip
    // line with no dynamic content -- same short-circuit idea as an item/enchantment name, just
    // for headers vanilla builds elsewhere in ItemStack/PotionContents/SmithingTemplateItem.
    // Verified against the real en_us.json shipped in the client jar and the decompiled callers
    // (see tools/verify-official-translation). Adding a key here is always safe: one that never
    // matches the currently rendered line just falls through to the AI path unchanged.
    private static final String[] KNOWN_FLAT_TOOLTIP_KEYS = {
            // ItemStack.addAttributeTooltips(), one per EquipmentSlotGroup: "When in Main Hand"
            "item.modifiers.any", "item.modifiers.mainhand", "item.modifiers.offhand", "item.modifiers.hand",
            "item.modifiers.feet", "item.modifiers.legs", "item.modifiers.chest", "item.modifiers.head",
            "item.modifiers.armor", "item.modifiers.body",
            // PotionContents.addPotionTooltip(): header shown above potion-effect lines on
            // potions, tipped arrows, suspicious stew, etc. -- "When Applied:"
            "potion.whenDrank",
            // SmithingTemplateItem.appendHoverText(): "Applies to:" / "Ingredients:" -- only the
            // title lines, not the value lines below them (those are prefixed with a leading
            // space and differ per template, so they're left to the AI path).
            "item.smithing_template.applies_to", "item.smithing_template.ingredients",
    };

    /**
     * Same short-circuit idea as {@link #tryOfficialTranslationForItemName}, but for a
     * standalone header line with no dynamic content -- see {@link #KNOWN_FLAT_TOOLTIP_KEYS}.
     * Does NOT cover lines that interpolate a formatted value into a translatable template
     * (e.g. "Attack Speed: +1.5", "Luck (00:37)") -- those need actual template substitution to
     * reconstruct, not just a flat key lookup; see tryOfficialTranslationForAttributeModifierLine
     * for the one such case this mod does handle.
     */
    public static boolean tryOfficialTranslationForKnownFlatLine(String renderedText) {
        String currentGameLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String targetLanguage = resolveTargetLanguage();
        for (String key : KNOWN_FLAT_TOOLTIP_KEYS) {
            String officialTranslation = OfficialTranslationLookup.lookup(key, currentGameLanguage, targetLanguage, renderedText);
            if (officialTranslation != null) {
                translationCache.put(keyFor(renderedText), officialTranslation);
                cacheDirty = true;
                LOGGER.debug("Used official translation for " + key + ": " + renderedText + " -> " + officialTranslation);
                return true;
            }
        }
        return false;
    }

    /**
     * Same short-circuit idea, for an attribute-modifier VALUE line ("Attack Speed: +1.5").
     * Reconstructs vanilla's own parameterized template (verified against the real en_us.json
     * shipped in the client jar, e.g. "attribute.modifier.plus.0" = "+%s %s") rather than a
     * simple flat key; see OfficialTranslationLookup.lookupAttributeModifierLine for the details.
     * Requires the local player for the common "Attack Damage"/"Attack Speed" lines specifically
     * (those add the player's own base attribute value); a null player just means those two won't
     * match, not a crash.
     */
    public static boolean tryOfficialTranslationForAttributeModifierLine(ItemStack stack, String renderedText) {
        if (stack == null || stack.isEmpty()) return false;

        String currentGameLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String officialTranslation = OfficialTranslationLookup.lookupAttributeModifierLine(
                stack, Minecraft.getInstance().player, currentGameLanguage, resolveTargetLanguage(), renderedText);
        if (officialTranslation == null) return false;

        translationCache.put(keyFor(renderedText), officialTranslation);
        cacheDirty = true;
        LOGGER.debug("Used official attribute modifier translation: " + renderedText + " -> " + officialTranslation);
        return true;
    }

    /**
     * Shared "what should this tooltip line show" decision, used by BOTH tooltip systems this
     * mod hooks: the vanilla inventory tooltip (RenderTooltipEvent) and the Jade in-world
     * tooltip (TestTooltipCollectedCallback). Before #20 each call site duplicated this chain by
     * hand, and the Jade path had silently fallen behind -- it never got isModNameLine or any of
     * the official-lookup short-circuits (#12/#14/#15/#18), so hovering an item with Jade (as
     * opposed to hovering it in an inventory slot) always sent every line to the AI, including
     * plain "Minecraft"/"FTB Quests" mod-name lines the AI just echoes back unchanged. Routing
     * both call sites through one method means a lookup added here can never miss one of them
     * again.
     *
     * isFirstLine should be true only for the line carrying the item/block's own display name
     * (index 0 in both tooltip systems). Returns the text to append if a translation is already
     * available (cache hit or an official-lookup hit), or null if nothing is available yet -- an
     * AI request may have been kicked off in that case (unless {@link #isModNameLine} matched,
     * in which case the caller should skip the line entirely; check that separately first).
     */
    public static String resolveOrRequestTranslation(ItemStack stack, String renderedText, boolean isFirstLine) throws IOException, InterruptedException {
        if (textInCache(renderedText)) return getTranslationFromCache(renderedText);
        if (isFirstLine && tryOfficialTranslationForItemName(stack, renderedText)) return getTranslationFromCache(renderedText);
        if (!isFirstLine && tryOfficialTranslationForEnchantmentLine(stack, renderedText)) return getTranslationFromCache(renderedText);
        if (!isFirstLine && tryOfficialTranslationForKnownFlatLine(renderedText)) return getTranslationFromCache(renderedText);
        if (!isFirstLine && tryOfficialTranslationForAttributeModifierLine(stack, renderedText)) return getTranslationFromCache(renderedText);

        if (isFirstLine) {
            requestTranslateItemStackToTraditionalChinese(renderedText, stack);
        } else {
            requestTranslateToTraditionalChinese(renderedText);
        }
        return null;
    }

    public static void requestTranslateItemStackToTraditionalChinese(String textInEnglish, ItemStack stack) throws IOException, InterruptedException {
        if (stack != null && !IN_FLIGHT.contains(textInEnglish) && Config.ENABLE_ICON_CONFIG.get()) {
            RenderSystem.recordRenderCall(() -> {
                String image = getItemStackImage(stack);
                try {
                    requestTranslateToTraditionalChinese(textInEnglish, image, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            requestTranslateToTraditionalChinese(textInEnglish);
        }

    }

    public static void requestTranslateToTraditionalChinese(String textInEnglish,
                                                            String image,
                                                            boolean isScreenShot)
            throws IOException, InterruptedException {

        String fixedText = textInEnglish;

        if (TargetLanguage.isAlreadyInTargetLanguage(resolveTargetLanguage(), fixedText)) {
            translationCache.put(keyFor(fixedText), "");
            cacheDirty = true;
            // temporary diagnostic logging (same reason as the other [DIAG] lines: this early
            // return happens BEFORE those, so without this the log would look "clean" for a case
            // that actually took this branch, hiding rather than revealing the real cause)
            LOGGER.info("[DIAG] resolvedTargetLanguage=" + resolveTargetLanguage()
                    + " already in target language, skipping translation: " + fixedText);
            return;
        }

        if (isScreenShot) {
            if (screenshotTranslating) return;
        } else {
            if (IN_FLIGHT.contains(fixedText)) return;
            Long retryAfter = RETRY_AFTER.get(fixedText);
            if (retryAfter != null) {
                if (System.currentTimeMillis() < retryAfter) return; // still backing off after a 429
                RETRY_AFTER.remove(fixedText);
            }
        }

        // temporary diagnostic logging (issue: translation comes back unchanged/English even
        // though the game language is zh_tw) -- LOGGER.debug doesn't show up at NeoForge's
        // default log level, hence .info() here so it actually lands in logs/latest.log.
        LOGGER.info("[DIAG] endpoint=" + Config.ENDPOINT_CONFIG.get()
                + " resolvedTargetLanguage=" + resolveTargetLanguage()
                + " followGameLanguage=" + Config.FOLLOW_GAME_LANGUAGE.get()
                + " configTargetLanguage=" + Config.TARGET_LANGUAGE.get()
                + " gameSelectedLanguage=" + Minecraft.getInstance().getLanguageManager().getSelected()
                + " textToTranslate=" + fixedText);

        // Unified blank-text guard (previously only Gemini's own setupRequest had this -- the
        // Ollama/Mistral builders just returned null on blank text without caching "", so blank
        // text would silently re-attempt and re-log a warning every single time it was
        // encountered; consolidating onto the adapter-agnostic call site is also what fixes that).
        if (fixedText.isBlank()) {
            translationCache.put(keyFor(fixedText), "");
            cacheDirty = true;
            return;
        }

        Config.EndPoint endpoint = Config.ENDPOINT_CONFIG.get();
        TranslationProviderAdapter adapter = ProviderAdapterRegistry.forEndpoint(endpoint);

        HttpRequest request;
        boolean visionUnsupportedForScreenshot = false;
        try {
            ProviderSettings settings = resolveActiveProviderSettings(endpoint);

            // Vision-capability gate (mailbox review round 017, point O1): supportsVision was
            // being collected (ModelPreset.supportsVision, Config.CUSTOM_PROVIDER_SUPPORTS_VISION)
            // but never actually consulted here -- every image was attached unconditionally
            // whenever ENABLE_ICON_CONFIG/Screenshot Translation was on, regardless of whether the
            // selected model could do anything with it. The two callers need different treatment
            // because they have different fallbacks available:
            if (image != null && !settings.supportsVision()) {
                if (isScreenShot) {
                    // No text-only fallback exists for screenshot translation -- the image IS the
                    // payload, there is nothing else to send. Sending it anyway would just cost a
                    // request that 400s (or is silently ignored) on every single attempt until the
                    // player figures out why nothing happens; telling them clearly, once, and not
                    // sending the doomed request at all is strictly better (acceptance test 11).
                    // Flagged rather than returned directly so this stays inside the one try/catch
                    // that already owns "don't let anything here crash the render thread".
                    visionUnsupportedForScreenshot = true;
                    request = null;
                } else {
                    // Item icon (tooltip line 0): a text-only fallback DOES exist -- just don't
                    // attach the image and translate the name as plain text (acceptance test 10).
                    // No message shown; this is the expected, silent-degrade path, not an error.
                    image = null;
                    request = buildRequest(adapter, settings, fixedText, image, isScreenShot, endpoint);
                }
            } else {
                request = buildRequest(adapter, settings, fixedText, image, isScreenShot, endpoint);
            }
        } catch (Exception e) {
            // Reachable today via: Custom Provider with a blank/malformed base URL (see
            // OpenAiCompatibleAdapter.resolveSpec), or -- in principle, should be unreachable in
            // practice -- ProviderConfigResolver.resolve() finding no Config.PROVIDER_KEYS entry
            // for this endpoint. Either way, must never crash the render thread.
            LOGGER.warn("Failed to build translation request for endpoint " + endpoint + ": " + e.getMessage());
            return;
        }

        if (visionUnsupportedForScreenshot) {
            // Deliberately NOT gated by a one-shot hasShowXxxError flag the way the other
            // showMessage() calls in this file are (mailbox review round 017, point P2): those
            // flags exist to stop the render loop from spamming the same message every frame while
            // hovering an item -- but screenshot translation is player-triggered by a single key
            // press, already de-duplicated by screenshotTranslating (no concurrent re-entry), so
            // there's no flood risk to guard against. Gating it anyway created a real dead end: a
            // player who disabled tooltip translation and only uses Screenshot Translation would
            // never trigger handleHttpResponse() (the flag's only reset point), so after the first
            // "not supported" message, every subsequent press would silently do nothing at all --
            // exactly the "why isn't this doing anything" confusion this message exists to prevent.
            //
            // Also localized via a real per-locale lang key (mailbox review round 017, point P1),
            // not the bilingual (en, zh) literal pair every OTHER showMessage() call in this file
            // still uses -- this is a genuinely new message added after the 10-locale lang
            // infrastructure already existed for this round's work, so it doesn't inherit the
            // pre-existing bilingual convention the rest of Translator.java's chat messages still
            // have (that broader rewrite is tracked separately, see mailbox review #002 point G1).
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.translatable(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported")
                                    .withStyle(ChatFormatting.YELLOW));
                }
            });
            return; // before screenshotTranslating is ever set true, so no cleanup needed
        }

        if (!REQUEST_RATE_LIMITER.tryAcquire(Config.MAX_REQUESTS_PER_MINUTE.get(), System.currentTimeMillis())) return; // over the per-minute budget; a later render tick retries
        if (!CONCURRENCY_LIMIT.tryAcquire()) return; // too many requests already in flight; a later render tick retries

        // acquire/release must stay paired 1:1 with this exact ordering; a mismatch here isn't
        // caught by any automated test (see the disclosed limitation at the top of
        // tools/verify-concurrency/VerifyConcurrency.java) -- review this finally block by eye
        // before changing it.
        if (isScreenShot) screenshotTranslating = true; else IN_FLIGHT.add(fixedText);

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, throwable) -> {
                    try {
                        if (throwable != null) {
                            handleConnectionError(throwable);
                            return;
                        }

                        handleHttpResponse(resp, fixedText, isScreenShot, adapter);

                    } finally {
                        CONCURRENCY_LIMIT.release();
                        if (isScreenShot) screenshotTranslating = false; else IN_FLIGHT.remove(fixedText);
                    }
                });
    }

    private static void handleHttpResponse(HttpResponse<String> resp,
                                           String text,
                                           boolean isScreenShot,
                                           TranslationProviderAdapter adapter) {

        String responseText = resp.body();
        String translatedText;

        // temporary diagnostic logging, see the [DIAG] log in requestTranslateToTraditionalChinese
        LOGGER.info("[DIAG] response status=" + resp.statusCode() + " body=[" + responseText + "]");

        try {
            // Parsing is dispatched by WHICH adapter built the request, never by sniffing the
            // response body's shape -- several OpenAI-compatible-shaped providers share the exact
            // same choices[0].message.content response shape, so shape-sniffing would have become
            // ambiguous the moment a second one existed.
            translatedText = adapter.parseTranslationResponse(responseText);
        } catch (Exception e) {
            LOGGER.warn("Error parsing response: " + responseText);
            handleHttpError(resp.statusCode(), text, isScreenShot);
            return;
        }

        resetHttpErrorFlags();
        if (!isScreenShot) RETRY_ATTEMPTS.remove(text);

        LOGGER.info("[DIAG] parsed translatedText=[" + translatedText + "]");

        if (translatedText == null || translatedText.isBlank()) return;

        translatedText = cleanText(translatedText);

        if (!isScreenShot) {
            translationCache.put(keyFor(text), translatedText);
            cacheDirty = true;
            LOGGER.debug("Translated: " + text + " -> " + translatedText);
        } else {
            showScreenShotResult(translatedText);
        }

        LOGGER.debug("status: " + resp.statusCode());
    }

    private static void handleHttpError(int statusCode, String text, boolean isScreenShot) {

        switch (statusCode) {

            case 403 -> showMessage(
                    "Translation failed! Check Your API Key in config!",
                    "無法翻譯! 請檢查你的 config 資料夾的 API KEY",
                    ChatFormatting.YELLOW,
                    () -> hasShowAPIKEYError,
                    () -> hasShowAPIKEYError = true
            );

            case 429 -> {
                if (!isScreenShot) scheduleRetryBackoff(text);
                showMessage(
                        "Translation failed! You request too frequently (RPM exceeded)",
                        "無法翻譯! 請求過快導致超過 RPM 限制",
                        ChatFormatting.YELLOW,
                        () -> hasShowRequestTooFrequentError,
                        () -> hasShowRequestTooFrequentError = true
                );
            }

            default -> showMessage(
                    "Translation failed! HTTP Status Code: " + statusCode,
                    "翻譯失敗! HTTP 回傳碼: " + statusCode,
                    ChatFormatting.RED,
                    () -> hasShowOtherError,
                    () -> hasShowOtherError = true
            );
        }
    }

    // simple exponential backoff (4s, 8s, 16s, capped at 30s) before this exact text is allowed
    // to be retried again, so a burst of 429s doesn't just get retried every render frame.
    private static void scheduleRetryBackoff(String text) {
        int attempt = RETRY_ATTEMPTS.merge(text, 1, Integer::sum);
        RETRY_AFTER.put(text, System.currentTimeMillis() + RetryPolicy.backoffDelayMs(attempt));
    }

    private static void handleConnectionError(Throwable throwable) {

        LOGGER.warn("Translation request failed: " + throwable.getMessage());

        showMessage(
                "Translate failed! Check Your Internet Connection",
                "無法翻譯! 請檢查網路連線",
                ChatFormatting.YELLOW,
                () -> hasShowConnectionError,
                () -> hasShowConnectionError = true
        );
    }

    private static void showMessage(String en,
                                    String zh,
                                    ChatFormatting color,
                                    BooleanSupplier flagCheck,
                                    Runnable flagSet) {

        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null && !flagCheck.getAsBoolean()) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal(en).withStyle(color));
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal(zh).withStyle(color));
                flagSet.run();
            }
        });
    }

    private static void showScreenShotResult(String translatedText) {

        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("螢幕翻譯結果:\n" + translatedText)
                                .withStyle(Translator.translatedStyle)
                );
                sendDataToScreen(translatedText);
            }
        });
    }

    private static String cleanText(String text) {
        return text.replace("\n", " ")
                .replaceAll("\\p{Cntrl}", "")
                .trim();
    }

    private static void resetHttpErrorFlags() {
        hasShowAPIKEYError = false;
        hasShowRequestTooFrequentError = false;
        hasShowOtherError = false;
        hasShowConnectionError = false;
    }

    private static void sendDataToScreen(String finalTranslatedText) {

        ScreenEventRender.setRenderText(finalTranslatedText);
    }
}
