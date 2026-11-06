package net.github.dctime.mixin.ftbquests;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.misc.CompactGridLayout;
import dev.ftb.mods.ftbquests.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.github.dctime.Config;
import net.github.dctime.libs.*;
import net.github.dctime.libs.ftbquests.FormattedTextGetterSetter;
import net.github.dctime.libs.ftbquests.ICloseViewQuestButton;
import net.github.dctime.libs.ftbquests.IPinViewQuestButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(ViewQuestPanel.class)
// 1.19.2: FTB Quests 1902.x's ViewQuestPanel is a plain Panel (not yet a ModalPanel), lives in
// dev.ftb.mods.ftbquests.gui.quests, exposes panelRewards as a real field (so it's shadowed
// instead of captured from addWidgets' locals), and has no onClosed() of its own (so it's
// overridden below instead of injected into).
public abstract class ViewQuestPanelMixin extends Panel {

    @Shadow(remap = false)
    private TextField titleField;
    @Shadow(remap = false)
    private BlankPanel panelText;
    @Shadow(remap = false)
    private BlankPanel panelTasks;

    @Shadow(remap = false)
    public BlankPanel panelRewards;


    @Shadow(remap = false)
    private final QuestScreen questScreen;
    @Shadow(remap = false)
    private Quest quest;
    @Shadow(remap = false)
    private BlankPanel panelContent;


    @Shadow(remap = false)
    private long lastScrollTime;

    private boolean isViewQuestPanelTranslated = false;
    private List<Boolean> isDescriptionTranslated = null;
    // -1 : standup, 0 : ready to resize, 1+: amount of translation left
    private int translationLeft = -1;

    // Tracks the parent's size as of the last resizeUI() call -- resizeUI() only used to run right
    // after translation finished, so toggling fullscreen (or any other window resize) later, while
    // the quest window stays open with translation already done, left height/position frozen at the
    // old screen size: a real gap between the modal's old bottom edge and the new, taller screen.
    // onDraw runs every frame regardless of translation state, so it's used here to notice a parent
    // resize and re-run resizeUI() to re-clamp/re-center for the new dimensions.
    private int lastParentWidth = -1;
    private int lastParentHeight = -1;

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewQuestPanel.class);

    private void resetQuest() {
        titleField = null;
        panelText = null;
        panelTasks = null;
        panelRewards = null;
        panelContent = null;
        refreshWidgets();
    }

    private ViewQuestPanelMixin(Panel panel, QuestScreen questScreen) {
        super(panel);
        this.questScreen = questScreen;
    }



    private void resizeUI() {
        // Translation successful, do some ui adjustment
        // title width limit

        if (this.quest == null) return;

        int width = Math.max(200, Math.max(this.titleField.width + 54, this.width));
        if (this.quest.minWidth > 0) {
            width = Math.max(this.quest.minWidth, width);
        }
//                else if (this.questScreen.getSelectedChapter().isPresent()) {
//                    if (this.questScreen.getSelectedChapter().get().getDefaultMinWidth() > 0) {
//                        width = Math.max(this.questScreen.getSelectedChapter().get().getDefaultMinWidth(), width);
//                    }
//                }
        this.setWidth(Math.max(width, this.width));
//                this.panelContent.setPosAndSize(0, Math.max(16, this.titleField.height + 8), this.width, 0);
//                this.panelContent.setHeight(this.height - 17);
        // TODO: Get quest screen and modify modal panel width
        // NOTE: NO NEED CUZ modalPanel is already viewQuestPanel
//                if (this.questScreen instanceof ModalPanelsGetter modalPanelsGetter) {
//                    ModalPanel modalPanel = modalPanelsGetter.getModalPanels().getFirst();
//                    if (modalPanel != null) {
//                        modalPanel.setWidth(this.width);
//                    }
//                }
        this.setPos((this.parent.width - this.width) / 2, (this.parent.height - this.height) / 2);
        int w2 = this.width/2;

//                this.panelTasks.setPosAndSize(2, 16, w2 - 3, this.panelTasks.height);
//                this.panelRewards.setPosAndSize(w2 + 2, 16, w2 - 3, this.panelRewards.height);
//
        // tasks rewards ui resize
        int bsize = 18;
//                int at = this.panelTasks.align(new CompactGridLayout(bsize + 2));
//                int ar = this.panelRewards.align(new CompactGridLayout(bsize + 2));
//                int height = Math.max(at, ar);

        this.titleField.setPosAndSize(27, 4, this.width - 54, this.titleField.height);
        this.panelTasks.setPosAndSize(2, 16, w2 - 3, this.panelTasks.height);
        this.panelRewards.setPosAndSize(w2 + 2, 16, w2 - 3, this.panelTasks.height);
        int at = this.panelTasks.align(new CompactGridLayout(bsize + 2));
        int ar = this.panelRewards.align(new CompactGridLayout(bsize + 2));
        int height = Math.max(at, ar);
        this.panelTasks.setHeight(height);
        this.panelRewards.setHeight(height);
        int tox = (this.panelTasks.width - this.panelTasks.getContentWidth()) / 2;
        int rox = (this.panelRewards.width - this.panelRewards.getContentWidth()) / 2;
        int toy = (this.panelTasks.height - this.panelTasks.getContentHeight()) / 2;
        int roy = (this.panelRewards.height - this.panelRewards.getContentHeight()) / 2;

        for(Widget widgetTasks : this.panelTasks.widgets) {
            widgetTasks.setX(widgetTasks.posX + tox);
            widgetTasks.setY(widgetTasks.posY + toy);
        }

        for(Widget widgetRewards : this.panelRewards.widgets) {
            widgetRewards.setX(widgetRewards.posX + rox);
            widgetRewards.setY(widgetRewards.posY + roy);
        }

        for (int widgetID = 0; widgetID < this.panelContent.widgets.size(); widgetID++) {
            Widget contentWidget = this.panelContent.getWidget(widgetID);
            if (contentWidget instanceof TextField taskOrRewardTextField) {
                int TaskTextID = 3;
                int RewardTextID = 4;
                if (widgetID == TaskTextID) {
                    // Task text field
                    taskOrRewardTextField.setPosAndSize(2, 2, w2 - 3, 13);
                    taskOrRewardTextField.setMaxWidth(this.width);
                } else if (widgetID == RewardTextID) {
                    // Reward text field
                    taskOrRewardTextField.setPosAndSize(w2 + 2, 2, w2 - 3, 13);
                    taskOrRewardTextField.setMaxWidth(this.width);
                }
            }

            if (contentWidget instanceof ColorWidget boarderWidget) {
                if (widgetID == 5) {
                    boarderWidget.setPosAndSize(w2, 0, 1, 16 + height + 6);
                } else if (widgetID == 6) {
                    boarderWidget.setPosAndSize(1, 16 + height + 6, this.width - 2, 1);
                }
            }
        }

        this.panelContent.setPosAndSize(0, Math.max(16, this.titleField.height + 8), this.width, this.panelContent.height);
//                this.panelText.setHeight(this.panelText.align(new WidgetLayout.Vertical(0, 1, 2)));
//                this.setHeight(Math.min(this.panelContent.getContentHeight() + this.titleField.height + 12, this.parent.height - 10));


        // TODO: adjust everything that got width
//                this.panelTasks.setPosAndSize(2, 16, w2 - 3, this.panelTasks.height);
//                this.panelRewards.setPosAndSize(w2 + 2, 16, w2 - 3, this.panelTasks.width);

//                int at = this.panelTasks.align(new CompactGridLayout(bsize + 2));
//                int ar = this.panelRewards.align(new CompactGridLayout(bsize + 2));
//                int height = Math.max(at, ar);
//                this.panelText.setPosAndSize(3, 16 + height + 12, this.width - 6, 0);
//                this.titleField.setPosAndSize(27, 4, width - 54, this.titleField.height);
//                this.panelContent.setHeight(this.panelContent.align(new WidgetLayout.Vertical(0, 1, 2)));
        // blankpanel

        this.panelText.setHeight(this.panelText.align(new WidgetLayout.Vertical(0, 1, 2)));
        this.panelText.setPosAndSize(3, 16 + height + 12, this.width - 6, this.panelText.height);
        for (Widget textWidget : this.panelText.widgets) {
            if (textWidget instanceof TextField textField) {
                textField.setMaxWidth(this.width - 6);
                textField.setWidth(this.width - 6);
            }
        }
        // Long translated text (e.g. a description that expands a lot in the target language) can
        // make panelText far taller than the screen -- without this clamp the modal just grew past
        // screen bounds with no scrollbar. Matches vanilla ViewQuestPanel#addWidgets' own clamp
        // (this.parent.height - 10), just measured from panelText's actual bottom edge since this
        // mixin's layout puts panelText directly under `this` rather than under panelContent.
        // ModalPanel/Panel already support scrolling overflowed content (Panel.movePanelScroll),
        // so clamping height here is what turns that into a working scrollbar.
        int contentBottom = this.panelText.posY + this.panelText.height;
        this.setHeight(Math.min(contentBottom, this.parent.height - 10));
        this.setPos(this.posX, (this.parent.height - this.height) / 2);
        this.lastParentWidth = this.parent.width;
        this.lastParentHeight = this.parent.height;
        int iconSize = Math.min(16, this.titleField.height + 2);
        for (Widget viewWidget : this.widgets) {
            if (viewWidget instanceof ICloseViewQuestButton) {
                viewWidget.setPosAndSize(this.width - iconSize - 2, 4, iconSize, iconSize);
            } else if (viewWidget instanceof IPinViewQuestButton) {
                viewWidget.setPosAndSize(this.width - iconSize * 2 - 4, 4, iconSize, iconSize);
            } else if (Objects.equals(viewWidget.getTitle(), Component.translatable("ftbquests.gui.no_dependants")) ||
                    Objects.equals(viewWidget.getTitle(), Component.translatable("ftbquests.gui.view_dependants"))) {
                viewWidget.setPosAndSize(this.width - 13, this.panelContent.posY + 2, 13, 13);
            }
        }
        // E2: scrolling used to be locked (mouseScrolled cancelled + setScrollY(0) every
        // frame) until every paragraph was translated -- with RPM exhausted, a provider in
        // cooldown or no network that was "forever", so a long quest's lower half could
        // never be read, not even in English. Scrolling now stays enabled; the only thing
        // the lock protected against is a translation landing (or a parent resize) and changing the content
        // height under the current scroll offset, so re-clamp it here (Panel.setScrollY
        // itself doesn't clamp; movePanelScroll does the same clamp on each wheel tick).
        setScrollY(Mth.clamp(getScrollY(), 0, Math.max(0, getContentHeight() - height)));
    }

    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    public void onDraw(PoseStack matrixStack, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!Config.ENABLE_FTB_QUEST_TRANSLATION.get()) return;
        // 1.19.2's draw() runs before the first addWidgets() (and while no quest is shown), when
        // these are still null.
        if (panelText == null || titleField == null) return;
        if (Translator.getDeletingTranslationKeyHold()) {
            resetQuest();
        }

        // The screen resizing (e.g. toggling fullscreen) while this modal is already open and fully
        // translated doesn't fire any callback here on its own -- resizeUI() otherwise only runs as
        // part of the translation-completion loop below. Re-run it once the parent's size actually
        // changes so the modal's clamp/centering (see resizeUI()) catches up with the new screen
        // size instead of leaving a stale gap where the screen grew.
        if (isDescriptionTranslated != null
                && (this.parent.width != lastParentWidth || this.parent.height != lastParentHeight)) {
            resizeUI();
        }

        translateTitle();

        // setup isDescriptionTranslated
        if (isDescriptionTranslated == null) {
            isDescriptionTranslated = new ArrayList<>(panelText.widgets.size());
            translationLeft = 0;
            for (int widgetIndex = 0; widgetIndex < panelText.widgets.size(); widgetIndex++) {
                if (panelText.widgets.get(widgetIndex) instanceof FormattedTextGetterSetter formattedTextGetterSetter) {
                    isDescriptionTranslated.add(false);
                    translationLeft++;
                    LOGGER.debug("Add Translation Task, total: " + translationLeft + "Text: " + (formattedTextGetterSetter.getFormattedText().length > 0 ? formattedTextGetterSetter.getFormattedText()[0].getString() + "length: " + formattedTextGetterSetter.getFormattedText().length : "empty"));
                } else {
                    isDescriptionTranslated.add(true);
                }
            }
//            return;
        }

        // send requests for all texts.
        for (int widgetIndex = 0; widgetIndex < isDescriptionTranslated.size(); widgetIndex++) {
            if (isDescriptionTranslated.get(widgetIndex)) continue;
            // not translated yet, so we need to translate it
            Widget widget = panelText.widgets.get(widgetIndex);

            if (!(widget instanceof FormattedTextGetterSetter formattedTextGetter)) return;
            if (translateFormattedText(formattedTextGetter)) {
                isDescriptionTranslated.set(widgetIndex, true);
                resizeUI();
                this.updateMouseOver(this.getMouseX(), this.getMouseY());
                translationLeft--;
                LOGGER.debug("Translation Task Done, left: " + translationLeft);
            }
        }

        if (translationLeft == 0) {
            translationLeft = -1;
        }
    }

    @Override
    public void onClosed() {
        LOGGER.debug("Warning OnClosed called, resetting translation state.");
        isViewQuestPanelTranslated = false;
        isDescriptionTranslated = null;
        translationLeft = -1;
        super.onClosed();
    }

    @Inject(method = "setCurrentPage", at = @At("RETURN"), remap = false)
    public void onSetCurrentPage(int page, CallbackInfo ci) {
        LOGGER.debug("Warning SetCurrentPage called, resetting translation state.");
        // Reset translation state when changing pages
        isViewQuestPanelTranslated = false;
        isDescriptionTranslated = null;
        translationLeft = -1;
    }

    @Override
    public void refreshWidgets() {
        super.refreshWidgets();
        isViewQuestPanelTranslated = false;
        isDescriptionTranslated = null;
        translationLeft = -1;
        LOGGER.debug("Warning refreshWidgets called, resetting translation state.");
    }

    private boolean translateFormattedText(FormattedTextGetterSetter formattedTextGetter) {
        if (formattedTextGetter.getFormattedText().length < 1) {
            LOGGER.debug("FormattedText is empty, cannot translate.");
            return true;
        }

        String totalText = "";
        for (int translateTextIndex = 0; translateTextIndex < formattedTextGetter.getFormattedText().length; translateTextIndex++) {
            String translateText = formattedTextGetter.getFormattedText()[translateTextIndex].getString();
            totalText = totalText + " " + translateText;
        }

        if (Translator.textInCache(totalText)) {
//            System.out.println("TOTALTEXT: " + totalText + " " + Translator.getTranslationFromCache(totalText));
            formattedTextGetter.setTranslatedFormattedText(Translator.getTranslationFromCache(totalText));
            LOGGER.debug("Using cached translation for: " + totalText + " -> " + Translator.getTranslationFromCache(totalText));

            return true;
        } else {
//            LOGGER.debug("Translating text: " + totalText);
            try {
                Translator.requestTranslateToTraditionalChinese(totalText);
            } catch (IOException ex) {
                LOGGER.warn("IO Exception while translating: " + ex.getMessage());
            } catch (InterruptedException ex) {
                LOGGER.warn("Interrupted Exception while translating: " + ex.getMessage());
            }
        }
        return false;
    }

    private void translateTitle() {
        if (isViewQuestPanelTranslated) {
//            System.out.println("Title field already translated, skipping translation.");
            return;
        }

        if (!(titleField instanceof FormattedTextGetterSetter formattedTextGetter) || formattedTextGetter.getFormattedText().length < 1) {
            LOGGER.warn("Title field is not an instance of FormattedTextGetter, cannot translate.");
            return;
        }

        if (translateFormattedText(formattedTextGetter)) {
            // Translation successful, set the translated flag
            isViewQuestPanelTranslated = true;
            resizeUI();
        }
    }
}
