package net.github.dctime.compability.jade;

import mcp.mobius.waila.api.Accessor;
import mcp.mobius.waila.api.BlockAccessor;
import mcp.mobius.waila.api.EntityAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.ui.IElement;
import mcp.mobius.waila.impl.ObjectDataCenter;
import mcp.mobius.waila.impl.ui.TextElement;
import net.github.dctime.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.github.dctime.libs.Translator;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Jade 5 (1.18.2) has no {@code JadeTooltipCollectedCallback} -- {@code mixin/jade/OverlayRendererMixin}
 * calls {@link #onTooltipCollected} at the head of every overlay render instead (and strips the
 * appended translation elements again at the tail, so they never pile up frame after frame), and
 * pulls the ItemStack from the overlay's icon element rather than from an accessor.
 */
public class TestJadeRenderEvent {

    public static void onTooltipCollected(ITooltip iTooltip, @Nullable ItemStack stack) {
        if (!Config.ENABLE_JADE_CONFIG.get()) return;
        // Entities (and item-less blocks) have no ItemStack icon, so isModNameLine(stack, ...)
        // can't see their "Minecraft" mod-name line; take the namespace from the looked-at target.
        String ownerNamespace = null;
        Accessor<?> accessor = ObjectDataCenter.get();
        if (accessor instanceof EntityAccessor entityAccessor) {
            ownerNamespace = Registry.ENTITY_TYPE.getKey(entityAccessor.getEntity().getType()).getNamespace();
        } else if (accessor instanceof BlockAccessor blockAccessor) {
            ownerNamespace = Registry.BLOCK.getKey(blockAccessor.getBlock()).getNamespace();
        }
        for (int jadeIndex = 0; jadeIndex < iTooltip.size(); jadeIndex++) {
            String lineMsg = "";
            List<IElement> elements = iTooltip.get(jadeIndex, IElement.Align.LEFT);
            for (IElement element : elements) {
                if (element instanceof TextElement textElement && textElement.getMessage() != null) {
                    lineMsg += textElement.getMessage().getString();
                }
            }
            // Jade 5 still builds its title/mod-name lines from legacy format strings (e.g. the
            // mod name is "§9§o%s"), so the raw text carries section-sign codes that newer
            // Jade versions express as Style instead. Strip them, or isModNameLine never matches
            // ("§9§oMinecraft" != "Minecraft") and every mod-name line goes to the AI, and
            // the codes would leak into the cache key and the prompt.
            lineMsg = ChatFormatting.stripFormatting(lineMsg);
            if (lineMsg == null || lineMsg.isBlank()) continue;

            // same short-circuit chain as the vanilla inventory tooltip (RenderTooltipEvent) --
            // see Translator#resolveOrRequestTranslation's javadoc for why this used to be
            // missing here (#20): a plain "Minecraft"/"FTB Quests" mod-name line, or any vanilla
            // item/enchantment/attribute-modifier line, was always sent to the AI when hovered
            // via Jade even though the exact same line already skipped the AI when hovered from
            // an inventory slot.
            if (Translator.isModNameLine(stack, lineMsg) || Translator.isModNameLine(ownerNamespace, lineMsg)) continue;

            String translated;
            try {
                translated = Translator.resolveOrRequestTranslation(stack, lineMsg, jadeIndex == 0);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (translated == null) continue; // nothing available this frame (AI request may be in flight)

            iTooltip.append(jadeIndex, new TextElement(new TextComponent(" " + translated).withStyle(Translator.translatedStyle)));
        }
    }
}
