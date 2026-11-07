package net.github.dctime.compability.jade;

import mcp.mobius.waila.overlay.RayTracing;
import mcp.mobius.waila.overlay.Tooltip;
import net.github.dctime.Config;
import net.github.dctime.libs.StringUtil;
import net.github.dctime.libs.TooltipRelated;
import net.github.dctime.libs.Translator;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Jade for 1.16.5 (2.x, a Hwyla fork -- still the {@code mcp.mobius.waila} API) has no
 * {@code JadeTooltipCollectedCallback} -- {@code mixin/jade/OverlayRendererMixin} calls
 * {@link #onTooltipCollected} at the head of every overlay render instead (and strips the appended
 * translation siblings again at the tail, so they never pile up frame after frame), and pulls the
 * ItemStack from the tooltip itself ({@code TooltipMixin}'s {@code IGetItem}) rather than from an
 * accessor.
 */
public class TestJadeRenderEvent {

    public static void onTooltipCollected(Tooltip tooltip, @Nullable ItemStack stack) {
        if (!Config.ENABLE_JADE_CONFIG.get()) return;
        // An entity has no ItemStack, so isModNameLine(stack, ...) can't see its "Minecraft"
        // mod-name line; take the namespace from the entity Jade is currently looking at.
        String ownerNamespace = null;
        if (RayTracing.INSTANCE.getTarget() instanceof EntityRayTraceResult) {
            Entity entity = RayTracing.INSTANCE.getTargetEntity();
            if (entity != null) ownerNamespace = Registry.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        }
        for (int jadeIndex = 0; jadeIndex < tooltip.getLines().size(); jadeIndex++) {
            ITextComponent line = tooltip.getLines().get(jadeIndex).getComponent();
            if (!(line instanceof TextComponent)) continue;
            TextComponent textComponent = (TextComponent) line;
            // TooltipRelated reads the line the way Jade 2.x builds it: its RenderableTextComponent
            // (item icons/health bars) and TaggedTextComponent placeholders serialize to opaque
            // strings via getString(), so those are skipped and only the readable text is kept.
            String lineMsg = TooltipRelated.getStringFromComponent(textComponent);
            // Jade still builds its title/mod-name lines from legacy format strings (e.g. the
            // mod name is "§9§o%s"), so the raw text carries section-sign codes that newer
            // Jade versions express as Style instead. Strip them, or isModNameLine never matches
            // ("§9§oMinecraft" != "Minecraft") and every mod-name line goes to the AI, and
            // the codes would leak into the cache key and the prompt.
            lineMsg = TextFormatting.stripFormatting(lineMsg);
            if (lineMsg == null || StringUtil.isBlank(lineMsg)) continue;

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

            try {
                textComponent.append(new StringTextComponent(" " + translated).withStyle(Translator.translatedStyle));
            } catch (UnsupportedOperationException e) {
                // RenderableTextComponent-style lines with an immutable sibling list -- leave untouched.
            }
        }
    }
}
