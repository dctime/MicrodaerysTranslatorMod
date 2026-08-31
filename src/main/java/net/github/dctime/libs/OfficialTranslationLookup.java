package net.github.dctime.libs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

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

    /**
     * Same idea as {@link #lookup}, but for an enchantment tooltip line ("Sharpness V"), which is
     * NOT a single translation key -- vanilla builds it from TWO keys concatenated with a space
     * (see Enchantment.getFullname): the enchantment's own name, plus "enchantment.level.&lt;N&gt;"
     * for the level, UNLESS the enchantment's max level is 1 and this instance is level 1 (e.g.
     * Mending), in which case there's no level suffix at all. Checks every enchantment on the
     * stack and returns the first one whose reconstructed current-language text matches what's
     * actually rendered. Deliberately does NOT handle potion/status-effect tooltip lines: those
     * embed a live remaining-duration string that changes every tick, so there is no stable text
     * to match against at all -- not a scope choice, a hard limitation of "match the whole
     * rendered line" against something that is never the same string twice.
     */
    @Nullable
    public static String lookupEnchantmentLine(ItemStack stack, String currentGameLanguageCode, String targetLanguageCode, String currentlyRenderedText) {
        ClientLanguage current = forLanguage(currentGameLanguageCode);
        ClientLanguage target = forLanguage(targetLanguageCode);

        // getTagEnchantments() (not the deprecated getEnchantments(), which is the exact same
        // behavior minus the @Deprecated marker): vanilla's own tooltip rendering
        // (ItemEnchantments.addToTooltip) reads the raw stored level, never
        // GetEnchantmentLevelEvent-hooked values -- we need the same raw values it does, or a
        // mod hooking that event could make our reconstructed text never match what's rendered.
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getTagEnchantments().entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = entry.getIntValue();
            String nameKey = translationKeyOf(enchantment.description());
            if (nameKey == null) continue; // description isn't a plain translatable component, can't match by key

            boolean needsLevel = level != 1 || enchantment.getMaxLevel() != 1;
            String levelKey = needsLevel ? "enchantment.level." + level : null;

            String result = decideEnchantmentLine(
                    current.has(nameKey) ? current.getOrDefault(nameKey, "") : null,
                    levelKey == null ? null : (current.has(levelKey) ? current.getOrDefault(levelKey, "") : null),
                    needsLevel,
                    currentlyRenderedText,
                    target.getOrDefault(nameKey, null),
                    levelKey == null ? null : target.getOrDefault(levelKey, null)
            );
            if (result != null) return result;
        }
        return null;
    }

    @Nullable
    private static String translationKeyOf(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    /**
     * Pure reconstruction-and-compare for one enchantment, pulled out so it can be unit-tested
     * without a running game -- see tools/verify-official-translation.
     */
    @Nullable
    public static String decideEnchantmentLine(@Nullable String currentName, @Nullable String currentLevelOrNull, boolean needsLevel,
                                                String currentlyRenderedText,
                                                @Nullable String targetName, @Nullable String targetLevelOrNull) {
        if (currentName == null) return null;
        String expectedCurrent = currentName;
        if (needsLevel) {
            if (currentLevelOrNull == null) return null;
            expectedCurrent = currentName + " " + currentLevelOrNull;
        }
        if (!expectedCurrent.equals(currentlyRenderedText)) return null;

        if (targetName == null) return null;
        if (needsLevel) {
            return targetLevelOrNull == null ? null : targetName + " " + targetLevelOrNull;
        }
        return targetName;
    }
}
