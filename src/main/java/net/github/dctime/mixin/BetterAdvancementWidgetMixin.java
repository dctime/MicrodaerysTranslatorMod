package net.github.dctime.mixin;

import betteradvancements.common.gui.BetterAdvancementTab;
import betteradvancements.common.gui.BetterAdvancementWidget;
import betteradvancements.common.gui.BetterAdvancementsScreen;
import betteradvancements.common.util.CriterionGrid;
import com.llamalad7.mixinextras.sugar.Local;
import net.github.dctime.Config;
import net.github.dctime.libs.Translator;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
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
    @Shadow
    @Final
    @Mutable
    private String title;

    @Shadow
    @Final
    private AdvancementNode advancementNode;

    @Shadow
    @Final
    private BetterAdvancementTab betterAdvancementTabGui;

    @Shadow
    private CriterionGrid criterionGrid;

    @Shadow
    private int width;

    @Shadow
    private AdvancementProgress advancementProgress;

    @Shadow
    private List<FormattedCharSequence> description;

    @Shadow
    @Final
    private DisplayInfo displayInfo;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    protected int y;
    @Shadow
    protected int x;

    private List<FormattedCharSequence> tempDescription;
    private String tempTitle;
    private String translatedTitle;
    private int tempMaxWidth;

    @Shadow
    protected abstract List<FormattedText> findOptimalLines(Component line, int width);

    @Inject(method = "drawHover", at = @At(value = "FIELD", target = "width", ordinal = 0))
    public void onDrawHover(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci) {
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
        if (!Translator.textInCache(this.title)) {
            Translator.requestTranslateToTraditionalChinese(this.title);
            return;
        }

        this.translatedTitle = " " + Translator.getTranslationFromCache(this.title);
        this.title = this.title + this.translatedTitle;

        // resize UI
        int k = 0;
        if (this.advancementNode.advancement().requirements().size() > 1) {
            int strLengthRequirementCount = String.valueOf(this.advancementNode.advancement().requirements().size()).length();
            k = Minecraft.getInstance().font.width("  ") + Minecraft.getInstance().font.width("0") * strLengthRequirementCount * 2 + Minecraft.getInstance().font.width("/");
        }

        int titleWidth = 29 + Minecraft.getInstance().font.width(this.title) + k;
        BetterAdvancementsScreen screen = this.betterAdvancementTabGui.getScreen();
        this.criterionGrid = CriterionGrid.findOptimalCriterionGrid(this.advancementNode.holder(), this.advancementNode.advancement(), this.advancementProgress, screen.width / 2, Minecraft.getInstance().font);

        if (CriterionGrid.requiresShift && !Screen.hasShiftDown()) {
            tempMaxWidth = titleWidth;
        } else {
            tempMaxWidth = Math.max(titleWidth, this.criterionGrid.width);
        }
    }
    private void translateDesc() throws IOException, InterruptedException {
        String originalDesc = this.displayInfo.getDescription().getString();
        if (!Translator.textInCache(originalDesc)) {
            Translator.requestTranslateToTraditionalChinese(originalDesc);
            return;
        }

        String translatedDesc = Translator.getTranslationFromCache(originalDesc);
        this.description = Language.getInstance().getVisualOrder(this.findOptimalLines(ComponentUtils.mergeStyles(this.displayInfo.getDescription().copy().append(Component.literal("\n"+translatedDesc).withStyle(Translator.translatedStyle)), Style.EMPTY.withColor(this.displayInfo.getType().getChatColor())), tempMaxWidth));

        for(FormattedCharSequence line : this.description) {
            tempMaxWidth = Math.max(tempMaxWidth, Minecraft.getInstance().font.width(line));
        }
    }

    @Inject(method = "drawHover", at = @At(value = "INVOKE", target = "drawString", ordinal = 0))
    public void drawHoverLeftNoS(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci, @Local(name = "drawX") int drawX) {
        guiGraphics.drawString(this.minecraft.font, this.translatedTitle, drawX + 5 + this.minecraft.font.width(this.title), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 0 called");
    }

//    @Inject(method = "drawHover", at = @At(value = "INVOKE", target = "drawString", ordinal = 1), locals = LocalCapture.CAPTURE_FAILHARD)
//    public void drawHoverLeftS(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci, @Local(name = "s") String s, @Local(name = "i") int i) {
//        guiGraphics.drawString(this.minecraft.font, this.translatedTitle, scrollX + this.x - i + this.minecraft.font.width(s), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 1 called");
//    }

    @Inject(method = "drawHover", at = @At(value = "INVOKE", target = "drawString", ordinal = 2))
    public void drawHoverRightNoS(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci) {
        guiGraphics.drawString(this.minecraft.font, this.translatedTitle, scrollX + this.x + 32 + this.minecraft.font.width(this.title), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 2 called");
    }

//    @Inject(method = "drawHover", at = @At(value = "INVOKE", target = "drawString", ordinal = 3), locals = LocalCapture.CAPTURE_FAILHARD)
//    public void drawHoverRightS(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci, @Local(name = "s") String s, @Local(name = "i") int i) {
//        guiGraphics.drawString(this.minecraft.font, this.translatedTitle, scrollX + this.x + this.width - i - 5 + this.minecraft.font.width(s), scrollY + this.y + 9, Translator.translatedStyle.getColor().getValue());
//        System.out.println("drawString 3 called");
//    }

    @Inject(method = "drawHover", at = @At(value = "RETURN"))
    public void endDrawHover(GuiGraphics guiGraphics, int scrollX, int scrollY, float fade, int left, int top, CallbackInfo ci) {
        this.translatedTitle = "";
        this.title = tempTitle;
        this.description = tempDescription.stream().toList();
    }
}
