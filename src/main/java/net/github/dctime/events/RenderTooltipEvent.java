package net.github.dctime.events;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.Translator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Inventory/JEI item tooltips. Forge 36 (1.16.5) has no {@code RenderTooltipEvent.GatherComponents}
 * (added in 1.17), so this listens to {@link ItemTooltipEvent} instead -- fired from
 * {@code ItemStack.getTooltipLines()}, i.e. every time any screen builds an item tooltip, same as
 * every earlier 1.16.5 release of this mod. LOWEST priority so lines other mods append in their own
 * ItemTooltipEvent listeners (lore, energy, mod-name lines) are already in the list and get
 * translated too, matching GatherComponents firing after all tooltip lines exist on newer versions.
 * <p>
 * Each translated line is REPLACED with a copy carrying the translation as an appended sibling,
 * never mutated in place: some items/mods hand out the same cached ITextComponent instance every
 * call, and appending to it directly would stack " 翻譯 翻譯 翻譯..." one more time per frame.
 */
@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class RenderTooltipEvent {

    private static final Logger LOGGER = LogManager.getLogger(RenderTooltipEvent.class);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Config.ENABLE_TOOLTIP_TRANSLATION.get()) return;
        ItemStack stack = event.getItemStack();
        List<ITextComponent> elements = event.getToolTip();

        for (int i = 0; i < elements.size(); i++) {
            ITextComponent text = elements.get(i);
            if (text == null) continue;
            // 1.16.5: JEI 7 (and other 1.16 mods) still append lines built from legacy format
            // strings -- JEI's mod-name line is literally "§9§oMinecraft" -- so the raw text carries
            // section-sign codes newer versions express as Style. Strip them (same fix as the Jade
            // path), or isModNameLine never matches and every item's mod-name line goes to the AI
            // (seen in a dev client: JEI indexing every item at world join fired ~3000 of them), and
            // the codes would leak into the cache key and the prompt.
            String original = TextFormatting.stripFormatting(text.getString());
            if (original == null) continue;
            if (Translator.isModNameLine(stack, original)) continue; // "Minecraft"/"FTB Quests": leave untouched, don't waste an AI call on a brand name
            String translated;
            try {
                translated = Translator.resolveOrRequestTranslation(stack, original, i == 0);
            } catch (IOException ex) {
                LOGGER.warn("IO Exception while translating: " + ex.getMessage());
                continue;
            } catch (InterruptedException ex) {
                LOGGER.warn("Interrupted Exception while translating: " + ex.getMessage());
                continue;
            }
            if (translated == null) continue; // nothing available this frame (AI request may be in flight)

            elements.set(i, text.copy().append(new StringTextComponent(" " + translated).setStyle(Translator.translatedStyle)));
        }
    }
}
