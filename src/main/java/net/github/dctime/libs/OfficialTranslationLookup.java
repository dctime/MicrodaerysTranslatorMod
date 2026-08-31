package net.github.dctime.libs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up official (mod/vanilla-shipped) translations for a translation key in an arbitrary
 * language, independent of whatever language the client is currently displaying. Backed by
 * {@link ClientLanguage#loadFrom}, which is what LanguageManager itself uses internally.
 *
 * IMPORTANT: loadFrom(resourceManager, List.of(a, b), ...) merges both languages into ONE map
 * where b overwrites a for shared keys -- calling it with two languages in one list would NOT let
 * us later ask "what was language A's value" separately from "what is language B's value" for the
 * same key. So every language involved is loaded as its own instance, never combined into a
 * single loadFrom() call.
 */
public class OfficialTranslationLookup {

    // keyed by language code; realistically holds at most a handful of entries in one session
    // (the game's current display language plus whatever target languages get requested).
    private static final Map<String, ClientLanguage> CACHE = new HashMap<>();

    private static ClientLanguage forLanguage(String languageCode) {
        return CACHE.computeIfAbsent(languageCode,
                code -> ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false));
    }

    /**
     * Drops all cached ClientLanguage snapshots so the next lookup() re-reads from the resource
     * manager. Must be called whenever resource packs are added/removed/reordered (e.g. a
     * translation resource pack), or this would keep answering from a stale, pre-reload snapshot
     * with no error or indication -- the same reload hook LanguageManager itself relies on. See
     * the RegisterClientReloadListenersEvent registration in MicrodaerysTranslatorClient.
     */
    public static void invalidateCache() {
        CACHE.clear();
    }

    /**
     * Official target-language translation for this key, but ONLY when the currently rendered
     * text matches the official value for the same key IN THE GAME'S CURRENT DISPLAY LANGUAGE --
     * i.e. this is genuinely the item/block's default name, not a custom rename (anvil, /data
     * component, ...) that just happens to share the same registry-derived translation key.
     * Comparing against the game's actual current language (not always English) is what lets this
     * work for e.g. a Chinese-language client translating a vanilla item into English or Japanese:
     * the currently rendered "羊毛" is compared against zh_tw's official value, not en_us's.
     * Returns null when not eligible; the caller should fall back to the AI translation path.
     */
    @Nullable
    public static String lookup(String translationKey, String currentGameLanguageCode, String targetLanguageCode, String currentlyRenderedText) {
        ClientLanguage current = forLanguage(currentGameLanguageCode);
        ClientLanguage target = forLanguage(targetLanguageCode);
        return decide(
                current.has(translationKey),
                current.getOrDefault(translationKey, ""),
                currentlyRenderedText,
                target.getOrDefault(translationKey, null)
        );
    }

    /**
     * The actual decision, pulled out as a pure function (no Minecraft classes involved) so it
     * can be unit-tested without a running game -- see tools/verify-official-translation.
     */
    @Nullable
    public static String decide(boolean currentLanguageHasKey, String currentLanguageValue, String currentlyRenderedText, @Nullable String targetValueOrNull) {
        if (!currentLanguageHasKey) return null;
        if (!currentLanguageValue.equals(currentlyRenderedText)) return null;
        return targetValueOrNull; // null here just means "target language has no official translation for this key"
    }
}
