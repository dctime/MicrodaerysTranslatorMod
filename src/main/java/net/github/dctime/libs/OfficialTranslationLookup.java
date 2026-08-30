package net.github.dctime.libs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Looks up official (mod/vanilla-shipped) translations for a translation key in an arbitrary
 * language, independent of whatever language the client is currently displaying. Backed by
 * {@link ClientLanguage#loadFrom}, which is what LanguageManager itself uses internally.
 *
 * IMPORTANT: loadFrom(resourceManager, List.of(a, b), ...) merges both languages into ONE map
 * where b overwrites a for shared keys -- calling it with ["en_us", target] would NOT let us
 * later ask "what was the en_us value" separately from "what is the target-language value" for
 * the same key. So en_us and the target language are each loaded as their own instance, never
 * combined into a single loadFrom() call.
 */
public class OfficialTranslationLookup {

    private static ClientLanguage enUsLanguage;

    private static String cachedTargetCode;
    private static ClientLanguage targetLanguage;

    private static ClientLanguage enUs() {
        if (enUsLanguage == null) {
            enUsLanguage = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of("en_us"), false);
        }
        return enUsLanguage;
    }

    private static ClientLanguage forTargetLanguage(String targetLanguageCode) {
        if (targetLanguage == null || !targetLanguageCode.equals(cachedTargetCode)) {
            cachedTargetCode = targetLanguageCode;
            targetLanguage = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(targetLanguageCode), false);
        }
        return targetLanguage;
    }

    /**
     * Drops both cached ClientLanguage snapshots so the next lookup() re-reads from the resource
     * manager. Must be called whenever resource packs are added/removed/reordered (e.g. a
     * translation resource pack), or this would keep answering from a stale, pre-reload snapshot
     * with no error or indication -- the same reload hook LanguageManager itself relies on. See
     * the RegisterClientReloadListenersEvent registration in MicrodaerysTranslatorClient.
     */
    public static void invalidateCache() {
        enUsLanguage = null;
        targetLanguage = null;
        cachedTargetCode = null;
    }

    /**
     * Official target-language translation for this key, but ONLY when the currently rendered
     * English text matches the official en_us value for the same key -- i.e. this is genuinely
     * the item/block's default name, not a custom rename (anvil, /data component, ...) that just
     * happens to share the same registry-derived translation key. Returns null when not
     * eligible; the caller should fall back to the AI translation path as usual.
     */
    @Nullable
    public static String lookup(String translationKey, String targetLanguageCode, String currentlyRenderedText) {
        ClientLanguage en = enUs();
        ClientLanguage target = forTargetLanguage(targetLanguageCode);
        return decide(
                en.has(translationKey),
                en.getOrDefault(translationKey, ""),
                currentlyRenderedText,
                target.getOrDefault(translationKey, null)
        );
    }

    /**
     * The actual decision, pulled out as a pure function (no Minecraft classes involved) so it
     * can be unit-tested without a running game -- see tools/verify-official-translation.
     */
    @Nullable
    public static String decide(boolean enUsHasKey, String enUsValue, String currentlyRenderedText, @Nullable String targetValueOrNull) {
        if (!enUsHasKey) return null;
        if (!enUsValue.equals(currentlyRenderedText)) return null;
        return targetValueOrNull; // null here just means "target language has no official translation for this key"
    }
}
