package net.github.dctime.compability.jade;

import com.mojang.blaze3d.systems.RenderSystem;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElement;
import snownee.jade.impl.ui.BoxElement;
import snownee.jade.impl.ui.ItemStackElement;
import snownee.jade.impl.ui.TextElement;

import java.io.IOException;
import java.util.List;


public class TestTooltipCollectedCallback implements JadeTooltipCollectedCallback {

    @Override
    public void onTooltipCollected(IBoxElement iBoxElement, Accessor<?> accessor) {
        ItemStack stack = null;
        if ((iBoxElement instanceof BoxElement boxElement) && (boxElement.getIcon() instanceof ItemStackElement itemStackElement)) {
            stack = itemStackElement.getItem();
        }

        if (!Config.ENABLE_JADE_CONFIG.get()) return;
        // Entities (and item-less blocks) have no ItemStack icon, so isModNameLine(stack, ...)
        // can't see their "Minecraft" mod-name line; take the namespace from the target itself.
        String ownerNamespace = null;
        if (accessor instanceof EntityAccessor entityAccessor) {
            ownerNamespace = BuiltInRegistries.ENTITY_TYPE.getKey(entityAccessor.getEntity().getType()).getNamespace();
        } else if (accessor instanceof BlockAccessor blockAccessor) {
            ownerNamespace = BuiltInRegistries.BLOCK.getKey(blockAccessor.getBlock()).getNamespace();
        }
        for (int jadeIndex = 0; jadeIndex < iBoxElement.getTooltip().size(); jadeIndex++) {
            String lineMsg = "";
            List<IElement> elements = iBoxElement.getTooltip().get(jadeIndex, IElement.Align.LEFT);
            for (IElement element : elements) {
                if (element instanceof TextElement textElement) {
                    lineMsg += textElement.getMessage();
                }
            }

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

            iBoxElement.getTooltip().append(jadeIndex, new TextElement(Component.literal(" " + translated).withStyle(Translator.translatedStyle)));
        }

    }
}
