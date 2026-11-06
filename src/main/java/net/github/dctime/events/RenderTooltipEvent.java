package net.github.dctime.events;

import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Inventory/JEI item tooltips. Forge 36 (1.16.5) has no {@code RenderTooltipEvent.GatherComponents}
 * (added in 1.17), so this listens to {@link ItemTooltipEvent} instead -- fired from
 * {@code ItemStack.getTooltipLines()}, same as every earlier 1.16.5 release of this mod. LOWEST
 * priority so lines other mods append in their own ItemTooltipEvent listeners (lore, energy,
 * mod-name lines) are already in the list and get translated too, matching GatherComponents firing
 * after all tooltip lines exist on newer versions.
 * <p>
 * <b>Only tooltips actually on screen may start an AI request.</b> Unlike GatherComponents,
 * ItemTooltipEvent also fires whenever any code merely ASKS for a tooltip: JEI 7 builds its search
 * index by calling getTooltipLines() on every registered item at world join (and other mods do the
 * same for searching), which used to fire one AI request per tooltip line of every item -- items
 * nobody was looking at -- and burned the player's whole per-minute quota on the spot. So every
 * real tooltip draw marks its stack via Forge's {@code RenderTooltipEvent.Pre} (fired by
 * vanilla's Screen.renderTooltip and by JEI's own tooltip drawing alike), and a line here may only
 * go to the AI when this event is for that same, just-drawn stack. The tooltip is rebuilt every
 * frame while hovered, so the very first frame (built before its own Pre fires) only uses
 * cache/official lookups and the request goes out one frame later. Cache hits and the official-
 * translation short-circuits cost nothing and are still applied to every tooltip. Container
 * pre-translation (Translator.pretranslateOpenContainerIfAny) is a separate, deliberate path and
 * is not affected.
 * <p>
 * Each translated line is REPLACED with a copy carrying the translation as an appended sibling,
 * never mutated in place: some items/mods hand out the same cached ITextComponent instance every
 * call, and appending to it directly would stack " 翻譯 翻譯 翻譯..." one more time per frame.
 */
@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class RenderTooltipEvent {

    private static final Logger LOGGER = LogManager.getLogger(RenderTooltipEvent.class);

    // How long a tooltip draw keeps its stack "on screen" for the purpose above. Tooltips are
    // redrawn every frame while hovered, so this only has to bridge one frame (even at very low
    // frame rates) -- short enough that a later off-screen getTooltipLines() for the same item
    // (e.g. a search) doesn't inherit it for long.
    private static final long RENDERED_TOOLTIP_WINDOW_NANOS = 500_000_000L;

    // A few recent draws, not just the last one: a frame can draw more than one item tooltip
    // (another mod's panel next to the hovered slot), and each must keep its own permission rather
    // than the later draw overwriting the earlier one's every frame. Render thread only.
    private static final int MAX_RENDERED_TOOLTIPS = 8;
    private static final Deque<ItemStack> renderedTooltipStacks = new ArrayDeque<>();
    private static final Deque<Long> renderedTooltipNanos = new ArrayDeque<>();

    /** Forge's RenderTooltipEvent.Pre: a tooltip for this stack is being drawn on screen right now. */
    @SubscribeEvent
    public static void onTooltipRendered(net.minecraftforge.client.event.RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getStack();
        if (stack == null || stack.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<ItemStack> stacks = renderedTooltipStacks.iterator();
        Iterator<Long> times = renderedTooltipNanos.iterator();
        while (stacks.hasNext()) {
            ItemStack seen = stacks.next();
            times.next();
            if (sameStack(seen, stack)) {
                stacks.remove();
                times.remove();
            }
        }
        renderedTooltipStacks.addFirst(stack.copy());
        renderedTooltipNanos.addFirst(now);
        while (renderedTooltipStacks.size() > MAX_RENDERED_TOOLTIPS) {
            renderedTooltipStacks.removeLast();
            renderedTooltipNanos.removeLast();
        }
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return ItemStack.isSame(a, b) && ItemStack.tagMatches(a, b);
    }

    private static boolean isTooltipOnScreen(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        long now = System.nanoTime();
        Iterator<ItemStack> stacks = renderedTooltipStacks.iterator();
        Iterator<Long> times = renderedTooltipNanos.iterator();
        while (stacks.hasNext()) {
            ItemStack seen = stacks.next();
            long at = times.next();
            if (now - at <= RENDERED_TOOLTIP_WINDOW_NANOS && sameStack(seen, stack)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Config.ENABLE_TOOLTIP_TRANSLATION.get()) return;
        // Off the render thread (a mod building tooltips on a worker for its search index) nothing
        // here is safe to touch -- the official-translation lookup caches are plain HashMaps.
        if (!RenderSystem.isOnRenderThread()) return;
        ItemStack stack = event.getItemStack();
        // No player = no world: a tooltip built for indexing at load time, never one on screen.
        boolean allowAiRequest = event.getPlayer() != null && isTooltipOnScreen(stack);
        List<ITextComponent> elements = event.getToolTip();

        for (int i = 0; i < elements.size(); i++) {
            ITextComponent text = elements.get(i);
            if (text == null) continue;
            // 1.16.5: JEI 7 (and other 1.16 mods) still append lines built from legacy format
            // strings -- JEI's mod-name line is literally "§9§oMinecraft" -- so the raw text carries
            // section-sign codes newer versions express as Style. Strip them (same fix as the Jade
            // path), or isModNameLine never matches and every item's mod-name line goes to the AI,
            // and the codes would leak into the cache key and the prompt.
            String original = TextFormatting.stripFormatting(text.getString());
            if (original == null) continue;
            if (Translator.isModNameLine(stack, original)) continue; // "Minecraft"/"FTB Quests": leave untouched, don't waste an AI call on a brand name
            String translated;
            try {
                translated = Translator.resolveOrRequestTranslation(stack, original, i == 0, allowAiRequest);
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
