package net.github.dctime.mixin;

import com.google.common.collect.ImmutableList;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {
    @Shadow
    @Final
    @Mutable
    private FormattedCharSequence title;

    @Shadow
    @Final
    @Mutable
    private List<FormattedCharSequence> description;

    @Shadow
    @Final
    @Mutable
    private int width;

    @Shadow
    @Final
    private DisplayInfo display;

    private int tempWidth;

    @Shadow
    @Final
    private AdvancementNode advancementNode;

    @Shadow
    protected abstract List<FormattedText> findOptimalLines(Component component, int maxWidth);


    @Inject(method = "drawHover", at = @At(value = "HEAD"))
    public void onDrawHover(GuiGraphics guiGraphics, int x, int y, float fade, int width, int height, CallbackInfo ci) {
        tempWidth = this.width;

        if (!Config.ENABLE_ADVANCEMENTS_CONFIG.get()) return;

        try {
            translateTitle();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // some var define
        int i = advancementNode.advancement().requirements().size();
        int j = String.valueOf(i).length();
        int k = i > 1 ? Minecraft.getInstance().font.width("  ") + Minecraft.getInstance().font.width("0") * j * 2 + Minecraft.getInstance().font.width("/") : 0;
        int l = 29 + Minecraft.getInstance().font.width(this.title) + k;
        this.description = Language.getInstance().getVisualOrder(this.findOptimalLines(ComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getType().getChatColor())), l));

        for(FormattedCharSequence formattedcharsequence : this.description) {
            l = Math.max(l, Minecraft.getInstance().font.width(formattedcharsequence));
        }

        // replace orignal description

        try {
            translateDesc(l);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // replace width

        for(FormattedCharSequence formattedcharsequence : this.description) {
            l = Math.max(l, Minecraft.getInstance().font.width(formattedcharsequence));
        }

        this.width = l + 3 + 5;
    }

    @Inject(method="drawHover", at = @At(value = "RETURN"))
    public void endDrawHover(GuiGraphics guiGraphics, int x, int y, float fade, int width, int height, CallbackInfo ci) {
        this.title = Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(display.getTitle(), 163));
        int i = advancementNode.advancement().requirements().size();
        int j = String.valueOf(i).length();
        int k = i > 1 ? Minecraft.getInstance().font.width("  ") + Minecraft.getInstance().font.width("0") * j * 2 + Minecraft.getInstance().font.width("/") : 0;
        int l = 29 + Minecraft.getInstance().font.width(this.title) + k;
        this.description = Language.getInstance().getVisualOrder(this.findOptimalLines(ComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getType().getChatColor())), l));
        this.width = tempWidth;
    }

    private void translateTitle() throws IOException, InterruptedException {
        // replace orignal title

        AtomicReference<String> titleOriginalText = new AtomicReference<>("");
        title.accept((var1, var2, var3) -> {
//            System.out.println("var1: " + var1 + ", var2: " + var2 + ", var3: " + var3);
            // no manual '\\' before '"' any more: every request builder already runs the prompt
            // through JsonUtil.escapeJson, so the extra backslash was double-escaped into the AI
            // input and the cache key (and diverged from BetterAdvancementWidgetMixin's raw-title key).
            titleOriginalText.set(titleOriginalText.get() + (char) var3);
            return true;
        });

//        System.out.println("Title: " + titleOriginalText.get());

        if (!Translator.textInCache(titleOriginalText.get())) {
            Translator.requestTranslateToTraditionalChinese(titleOriginalText.get());
            return;
        }

        String translated = Translator.getTranslationFromCache(titleOriginalText.get());
        // "" = "nothing to show" (same-language / echoed text): keep the vanilla title untouched.
        if (translated == null || translated.isBlank()) return;
//        FormattedCharSequence seq = FormattedCharSequence.forward(" "+translated, Translator.translatedStyle);
//        title = FormattedCharSequence.composite(title, seq);
        this.title = Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(
                FormattedText.composite(display.getTitle(), Component.literal(" "+translated).withStyle(Translator.translatedStyle)), 1000));
    }

    private void translateDesc(int j) throws IOException, InterruptedException {
        // E7: was rebuilt by concatenating the already-wrapped `description` lines, but vanilla's
        // StringSplitter drops the space it breaks on (splitAt(i, 1)), so "kill a monster" became
        // "kill amonster" in both the cache key and the AI input -- and the key changed with the
        // wrap width j (which grows once the title translation lands), re-requesting the same
        // description. The unwrapped source text is width-independent, same as
        // BetterAdvancementWidgetMixin.translateDesc already uses.
        String descText = display.getDescription().getString();

        if (!Translator.textInCache(descText)) {
            Translator.requestTranslateToTraditionalChinese(descText);
            return;
        }

        String translated = Translator.getTranslationFromCache(descText);
        // "" = "nothing to show": appending "\n" + "" would leave an empty line under the description.
        if (translated == null || translated.isBlank()) return;
        MutableComponent original = ComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getType().getChatColor()));
        this.description = Language.getInstance().getVisualOrder(this.findOptimalLines(original.append(Component.literal("\n"+translated).withStyle(Translator.translatedStyle)), j));
    }
}
