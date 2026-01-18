package net.github.dctime.mixin;

import betteradvancements.gui.BetterAdvancementTab;
import betteradvancements.gui.BetterAdvancementWidget;
import betteradvancements.gui.BetterAdvancementsScreen;
import betteradvancements.util.CriterionGrid;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.List;

@Mixin(BetterAdvancementWidget.class)
public abstract class BetterAdvancementWidgetMixin {
    @Shadow(remap = false)
    @Final
    @Mutable
    private String title;

    @Shadow(remap = false)
    @Final
    private Advancement advancement;

    @Shadow(remap = false)
    @Final
    private BetterAdvancementTab betterAdvancementTabGui;

    @Shadow(remap = false)
    private CriterionGrid criterionGrid;

    @Shadow(remap = false)
    private int width;

    @Shadow(remap = false)
    private AdvancementProgress advancementProgress;

    @Shadow(remap = false)
    private List<FormattedCharSequence> description;

    @Shadow(remap = false)
    @Final
    private DisplayInfo displayInfo;

    @Shadow(remap = false)
    @Final
    private Minecraft minecraft;

    @Shadow(remap = false)
    protected int y;
    @Shadow(remap = false)
    protected int x;

    private List<FormattedCharSequence> tempDescription;
    private String tempTitle;
    private String translatedTitle;
    private int tempMaxWidth;

    @Shadow(remap = false)
    protected abstract List<FormattedCharSequence> findOptimalLines(Component line, int width);
//    protected abstract List<FormattedText> findOptimalLines(Component line, int width);

    @Inject(method = "Lbetteradvancements/gui/BetterAdvancementWidget;drawHover(Lcom/mojang/blaze3d/vertex/PoseStack;IIFII)V", at = @At(value = "FIELD", target = "width", ordinal = 0), remap = false)
    public void onDrawHover(PoseStack poseStack, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci) {
        // end of line 276
//        System.out.println("Will this work");
        tempTitle = this.title;
        tempDescription = description.stream().toList();

        if (!Config.ENABLE_ADVANCEMENTS_CONFIG.get()) return;

        // tempMaxWidth stores changes in translatedTitle and translatedDesc
        tempMaxWidth = this.width;

        try {
            translateTitle();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            translateDesc();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        this.width = tempMaxWidth + 8;
        this.title = tempTitle; // render 一開始的 title 就好 translatedTitle 用另一個 Inject 渲染
    }

    private void translateTitle() throws IOException, InterruptedException {
        if (!Translator.translationCache.containsKey(this.title)) {
            Translator.requestTranslateToTraditionalChinese(this.title);
            return;
        }

        this.translatedTitle = " " + Translator.translationCache.get(this.title);
        this.title = this.title + this.translatedTitle;

        // resize UI
        int k = 0;
        if (this.advancement.getRequirements().length > 1) {
            int strLengthRequirementCount = String.valueOf(this.advancement.getRequirements().length).length();
            k = Minecraft.getInstance().font.width("  ") + Minecraft.getInstance().font.width("0") * strLengthRequirementCount * 2 + Minecraft.getInstance().font.width("/");
        }

        int titleWidth = 29 + Minecraft.getInstance().font.width(this.title) + k;
        BetterAdvancementsScreen screen = this.betterAdvancementTabGui.getScreen();
        this.criterionGrid = CriterionGrid.findOptimalCriterionGrid(this.advancement, this.advancementProgress, screen.width / 2, Minecraft.getInstance().font);

        if (CriterionGrid.requiresShift && !Screen.hasShiftDown()) {
            tempMaxWidth = titleWidth;
        } else {
            tempMaxWidth = Math.max(titleWidth, this.criterionGrid.width);
        }
    }
    private void translateDesc() throws IOException, InterruptedException {
        String originalDesc = this.displayInfo.getDescription().getString();
        if (!Translator.translationCache.containsKey(originalDesc)) {
            Translator.requestTranslateToTraditionalChinese(originalDesc);
            return;
        }

        String translatedDesc = Translator.translationCache.get(originalDesc);
//        this.description = Language.getInstance().getVisualOrder(this.findOptimalLines(ComponentUtils.mergeStyles(this.displayInfo.getDescription().copy().append(new TextComponent("\n"+translatedDesc).withStyle(Translator.translatedStyle)), Style.EMPTY.withColor(this.displayInfo.getFrame().getChatColor())), tempMaxWidth));
        this.description = this.findOptimalLines(ComponentUtils.mergeStyles(this.displayInfo.getDescription().copy().append(new TextComponent("\n"+translatedDesc).withStyle(Translator.translatedStyle)), Style.EMPTY.withColor(this.displayInfo.getFrame().getChatColor())), tempMaxWidth);

        for(FormattedCharSequence line : this.description) {
            tempMaxWidth = Math.max(tempMaxWidth, Minecraft.getInstance().font.width(line));
        }
    }

    @Inject(method = "Lbetteradvancements/gui/BetterAdvancementWidget;drawHover(Lcom/mojang/blaze3d/vertex/PoseStack;IIFII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I", ordinal = 0))
    public void drawHoverLeftNoS(PoseStack poseStack, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci, @Local(name = "drawX") int drawX) {
        // guiGraphics.drawString(this.minecraft.font, this.translatedTitle, drawX + 5 + this.minecraft.font.width(this.title), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
        this.minecraft.font.drawShadow(poseStack, this.translatedTitle, (float)(drawX + 5 + this.minecraft.font.width(this.title)), (float)(scrollY + this.y + 9), Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 0 called");
    }

//    @Inject(method = "drawHover", at = @At(value = "INVOKE", target = "drawString", ordinal = 1), locals = LocalCapture.CAPTURE_FAILHARD)
//    public void drawHoverLeftS(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci, @Local(name = "s") String s, @Local(name = "i") int i) {
//        guiGraphics.drawString(this.minecraft.font, this.translatedTitle, scrollX + this.x - i + this.minecraft.font.width(s), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 1 called");
//    }

    @Inject(method = "Lbetteradvancements/gui/BetterAdvancementWidget;drawHover(Lcom/mojang/blaze3d/vertex/PoseStack;IIFII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I", ordinal = 2))
    public void drawHoverRightNoS(PoseStack poseStack, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci) {
        // guiGraphics.drawString(this.minecraft.font, this.translatedTitle, scrollX + this.x + 32 + this.minecraft.font.width(this.title), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
        this.minecraft.font.drawShadow(poseStack, this.translatedTitle, (float)(scrollX + this.x + 32 + this.minecraft.font.width(this.title)), (float)(scrollY + this.y + 9), Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 2 called");
    }

//    @Inject(method = "drawHover", at = @At(value = "INVOKE", target = "drawString", ordinal = 3), locals = LocalCapture.CAPTURE_FAILHARD)
//    public void drawHoverRightS(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci, @Local(name = "s") String s, @Local(name = "i") int i) {
//        guiGraphics.drawString(this.minecraft.font, this.translatedTitle, scrollX + this.x + this.width - i - 5 + this.minecraft.font.width(s), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 3 called");
//    }

    @Inject(method = "Lbetteradvancements/gui/BetterAdvancementWidget;drawHover(Lcom/mojang/blaze3d/vertex/PoseStack;IIFII)V", at = @At(value = "RETURN"), remap = false)
    public void endDrawHover(PoseStack poseStack, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci) {
        this.translatedTitle = "";
        this.title = tempTitle;
        this.description = tempDescription.stream().toList();
    }
}
