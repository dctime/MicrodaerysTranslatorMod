package net.github.dctime.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.advancements.AdvancementEntryGui;
import net.minecraft.client.resources.Language;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.*;
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

@Mixin(AdvancementEntryGui.class)
public abstract class AdvancementWidgetMixin {
    @Shadow
    @Final
    @Mutable
    private IReorderingProcessor title;

    @Shadow
    @Final
    @Mutable
    private List<IReorderingProcessor> description;

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
    private Advancement advancement;

    @Shadow
    protected abstract List<ITextProperties> findOptimalLines(ITextComponent pComponent, int pMaxWidth);


    @Inject(method = "drawHover", at = @At(value = "HEAD"))
    public void onDrawHover(MatrixStack poseStack, int x, int y, float fade, int width, int height, CallbackInfo ci) {
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
        int i =advancement.getRequirements().length;
        int j = String.valueOf(i).length();
        int k = i > 1 ? Minecraft.getInstance().font.width("  ") + Minecraft.getInstance().font.width("0") * j * 2 + Minecraft.getInstance().font.width("/") : 0;
        int l = 29 + Minecraft.getInstance().font.width(this.title) + k;
        this.description = LanguageMap.getInstance().getVisualOrder(this.findOptimalLines(TextComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getFrame().getChatColor())), l));

        for(IReorderingProcessor formattedcharsequence : this.description) {
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

        for(IReorderingProcessor formattedcharsequence : this.description) {
            l = Math.max(l, Minecraft.getInstance().font.width(formattedcharsequence));
        }

        this.width = l + 3 + 5;
    }

    @Inject(method="drawHover", at = @At(value = "RETURN"))
    public void endDrawHover(MatrixStack poseStack, int x, int y, float fade, int width, int height, CallbackInfo ci) {
        this.title = LanguageMap.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(display.getTitle(), 163));
        int i = advancement.getRequirements().length;
        int j = String.valueOf(i).length();
        int k = i > 1 ? Minecraft.getInstance().font.width("  ") + Minecraft.getInstance().font.width("0") * j * 2 + Minecraft.getInstance().font.width("/") : 0;
        int l = 29 + Minecraft.getInstance().font.width(this.title) + k;
        this.description = LanguageMap.getInstance().getVisualOrder(this.findOptimalLines(TextComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getFrame().getChatColor())), l));
        this.width = tempWidth;
    }

    private void translateTitle() throws IOException, InterruptedException {
        // replace orignal title

        AtomicReference<String> titleOriginalText = new AtomicReference<>("");
        title.accept((var1, var2, var3) -> {
//            System.out.println("var1: " + var1 + ", var2: " + var2 + ", var3: " + var3);
            if (var3 == '"') titleOriginalText.set(titleOriginalText.get() + '\\');
            titleOriginalText.set(titleOriginalText.get() + (char) var3);
            return true;
        });

//        System.out.println("Title: " + titleOriginalText.get());

        if (!Translator.translationCache.containsKey(titleOriginalText.get())) {
            Translator.requestTranslateToTraditionalChinese(titleOriginalText.get());
            return;
        }

        String translated = Translator.translationCache.get(titleOriginalText.get());
//        FormattedCharSequence seq = FormattedCharSequence.forward(" "+translated, Translator.translatedStyle);
//        title = FormattedCharSequence.composite(title, seq);
        this.title = LanguageMap.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(
                ITextProperties.composite(display.getTitle(), new StringTextComponent(" "+translated).withStyle(Translator.translatedStyle)), 1000));
    }

    private void translateDesc(int j) throws IOException, InterruptedException {
        AtomicReference<String> descText = new AtomicReference<>("");
        for (int descIndex = 0; descIndex < description.size(); descIndex++) {
            IReorderingProcessor descSeq = description.get(descIndex);

            descSeq.accept((var1, var2, var3) -> {
                descText.set(descText.get() + (char) var3);
                return true;
            });
        }

        if (!Translator.translationCache.containsKey(descText.get())) {
            Translator.requestTranslateToTraditionalChinese(descText.get());
            return;
        }

        String translated = Translator.translationCache.get(descText.get());
        IFormattableTextComponent original = TextComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getFrame().getChatColor()));
        this.description = LanguageMap.getInstance().getVisualOrder(this.findOptimalLines(original.append(new StringTextComponent("\n"+translated).withStyle(Translator.translatedStyle)), j));
    }
}
