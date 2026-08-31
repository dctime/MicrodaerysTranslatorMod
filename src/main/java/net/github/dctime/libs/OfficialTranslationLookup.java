package net.github.dctime.libs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    /**
     * Same idea again, for an attribute-modifier VALUE line ("Attack Speed: +1.5"). Unlike the
     * header line (a single flat key, see Translator.tryOfficialTranslationForAttributeModifierHeaderLine),
     * vanilla builds this with Minecraft's OWN parameterized template system
     * (ItemStack.addModifierTooltip -> Component.translatable(key, number, attributeNameComponent)),
     * confirmed against the real en_us.json shipped in the client jar: e.g.
     * "attribute.modifier.plus.0" = "+%s %s". Reconstructing it needs three things vanilla itself
     * uses: which of equals/plus/take applies, the exact formatted number (reusing Mojang's own
     * ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT, not a reimplementation), and -- for the
     * common "Attack Damage"/"Attack Speed" lines specifically -- the LOCAL PLAYER's base
     * attribute value, since those two modifiers are additive on top of it (see the "flag" branch
     * below, mirroring ItemStack.addModifierTooltip exactly). A null player (no player context
     * available) just means those two special-cased modifiers won't match, not a crash.
     * Deliberately does NOT special-case Knockback Resistance's x10 display scaling: skipping it
     * means that one attribute's line simply won't match (falls through to AI) rather than being
     * a wrong "did the math slightly differently" risk for a rare case.
     */
    @Nullable
    public static String lookupAttributeModifierLine(ItemStack stack, @Nullable LivingEntity player, String currentGameLanguageCode, String targetLanguageCode, String currentlyRenderedText) {
        ClientLanguage current = forLanguage(currentGameLanguageCode);
        ClientLanguage target = forLanguage(targetLanguageCode);

        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            List<Map.Entry<Holder<Attribute>, AttributeModifier>> pairs = new ArrayList<>();
            stack.forEachModifier(group, (attribute, modifier) -> pairs.add(Map.entry(attribute, modifier)));

            for (Map.Entry<Holder<Attribute>, AttributeModifier> pair : pairs) {
                String result = decideAttributeModifierLine(pair.getKey(), pair.getValue(), player, current, target, currentlyRenderedText);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Nullable
    private static String decideAttributeModifierLine(Holder<Attribute> attributeHolder, AttributeModifier modifier, @Nullable LivingEntity player,
                                                        ClientLanguage current, ClientLanguage target, String currentlyRenderedText) {
        Attribute attribute = attributeHolder.value();
        double amount = modifier.amount();
        boolean isBaseAttackStat = false;
        if (player != null) {
            if (modifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                amount += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                isBaseAttackStat = true;
            } else if (modifier.is(Item.BASE_ATTACK_SPEED_ID)) {
                amount += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                isBaseAttackStat = true;
            }
        }

        double displayValue = (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                || modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                ? amount * 100.0
                : amount;

        String templateKeyPrefix;
        double valueToFormat;
        if (isBaseAttackStat) {
            templateKeyPrefix = "attribute.modifier.equals.";
            valueToFormat = displayValue;
        } else if (amount > 0.0) {
            templateKeyPrefix = "attribute.modifier.plus.";
            valueToFormat = displayValue;
        } else if (amount < 0.0) {
            templateKeyPrefix = "attribute.modifier.take.";
            valueToFormat = -displayValue;
        } else {
            return null; // amount == 0: vanilla doesn't render a line for this modifier at all
        }

        String templateKey = templateKeyPrefix + modifier.operation().id();
        String formattedNumber = ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(valueToFormat);
        String attributeNameKey = attribute.getDescriptionId();

        if (!current.has(templateKey) || !current.has(attributeNameKey)) return null;
        String expectedCurrent = substituteTemplate(current.getOrDefault(templateKey, ""), formattedNumber, current.getOrDefault(attributeNameKey, ""));
        if (expectedCurrent == null || !expectedCurrent.equals(currentlyRenderedText)) return null;

        String targetTemplate = target.getOrDefault(templateKey, null);
        String targetAttributeName = target.getOrDefault(attributeNameKey, null);
        if (targetTemplate == null || targetAttributeName == null) return null;
        return substituteTemplate(targetTemplate, formattedNumber, targetAttributeName);
    }

    /**
     * Reproduces TranslatableContents.decomposeTemplate()'s substitution semantics for the subset
     * this mod actually needs (sequential %s, literal %%) -- deliberately NOT java.util.Formatter/
     * String.format(): Minecraft's own parser (regex "%(?:(\d+)\$)?([A-Za-z%]|$)" in
     * TranslatableContents) differs from Java's format spec on edge cases, and reimplementing the
     * wrong one would silently reconstruct text that doesn't match what vanilla actually renders.
     * Returns null (don't guess) for anything not needed here: positional args ("%1$s") or any
     * conversion other than %s/%%. Pure, no Minecraft classes involved, unit-tested directly.
     */
    @Nullable
    public static String substituteTemplate(String template, String... args) {
        StringBuilder out = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '%') {
                out.append(c);
                i++;
                continue;
            }
            if (i + 1 >= template.length()) return null;
            char next = template.charAt(i + 1);
            if (next == '%') {
                out.append('%');
                i += 2;
            } else if (next == 's') {
                if (argIndex >= args.length) return null;
                out.append(args[argIndex++]);
                i += 2;
            } else {
                return null; // positional ref or unsupported conversion -- don't guess
            }
        }
        return out.toString();
    }
}
