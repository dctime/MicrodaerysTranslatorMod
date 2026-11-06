package net.github.dctime.mixin;

import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.IOException;

// #5: GuiGraphics.renderTooltip(Font, Component, int, int) goes straight to
// renderTooltip(Font, List<FormattedCharSequence>, ...) -> renderTooltipInternal, never through
// ClientHooks.gatherTooltipComponents, so RenderTooltipEvent.GatherComponents (this mod's only
// tooltip hook) never fires for it. Screens that show just an item's name this way (vanilla's own
// StatsScreen item column, and custom mod GUIs doing renderTooltip(font, stack.getHoverName(), x, y))
// therefore showed the English name with no translation at all. Every caller of that overload
// passes through this one method, so the translation is appended here, via the same
// resolveOrRequestTranslation chain the GatherComponents path uses.
// Deliberately NOT the List<FormattedCharSequence> overloads: those carry text already wrapped to
// width (widget Tooltip.create, chat hover), so each wrapped fragment would become its own AI request.
@Mixin(GuiGraphics.class)
public class GuiGraphicsTooltipMixin {

    @ModifyVariable(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V",
            at = @At("HEAD"), argsOnly = true)
    private Component microdaerystranslator$appendTranslation(Component text) {
        if (!Config.ENABLE_TOOLTIP_TRANSLATION.get()) return text;
        String raw = text.getString();
        // R3: this overload is also used for live-value tooltips ("1234 / 10000", "75%") whose
        // text changes every frame -- each value would be a new AI request and a permanent cache
        // entry. Text with no letter at all has nothing to translate; skip it before it reaches the
        // cache. (Values with any letter, e.g. "1234 / 10000 FE", still go through -- see RESULT.md.)
        if (raw.codePoints().noneMatch(Character::isLetter)) return text;
        String translated;
        try {
            // no ItemStack is available on this overload: isFirstLine=false keeps it off the
            // item-icon path (it would render an empty stack into a blank image).
            translated = Translator.resolveOrRequestTranslation(ItemStack.EMPTY, raw, false);
        } catch (IOException | InterruptedException e) {
            return text;
        }
        if (translated == null) return text;
        return text.copy().append(Component.literal(" " + translated).setStyle(Translator.translatedStyle));
    }
}
