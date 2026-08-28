package net.github.dctime.mixin.ftbquests;

import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.misc.CompactGridLayout;
import dev.ftb.mods.ftbquests.client.gui.quests.*;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.github.dctime.Config;
import net.github.dctime.libs.*;
import net.github.dctime.libs.ftbquests.FormattedTextGetterSetter;
import net.github.dctime.libs.ftbquests.ICloseViewQuestButton;
import net.github.dctime.libs.ftbquests.IPinViewQuestButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(ViewQuestPanel.class)
public abstract class ViewQuestPanelMixin extends ModalPanel {

    @Shadow
    private TextField titleField;
    @Shadow
    private BlankPanel panelText;
    @Shadow
    private BlankPanel panelTasks;
    @Shadow
    private BlankPanel panelRewards;
    @Shadow
    private final QuestScreen questScreen;
    @Shadow
    private Quest quest;
    @Shadow
    private BlankPanel panelContent;


    @Shadow
    private long lastScrollTime;

    @Shadow
    public abstract void addWidgets();

    private boolean isViewQuestPanelTranslated = false;
    private List<Boolean> isDescriptionTranslated = null;
    // -1 : standup, 0 : ready to resize, 1+: amount of translation left
    private int translationLeft = -1;

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

        int width = Math.max(200, Math.max(this.titleField.width + 54, this.getWidth()));
        if (this.quest.getMinWidth() > 0) {
            width = Math.max(this.quest.getMinWidth(), width);
        }
//                else if (this.questScreen.getSelectedChapter().isPresent()) {
//                    if (this.questScreen.getSelectedChapter().get().getDefaultMinWidth() > 0) {
//                        width = Math.max(this.questScreen.getSelectedChapter().get().getDefaultMinWidth(), width);
//                    }
//                }
        this.setWidth(Math.max(width, this.getWidth()));
//                this.panelContent.setPosAndSize(0, Math.max(16, this.titleField.height + 8), this.getWidth(), 0);
//                this.panelContent.setHeight(this.height - 17);
        // TODO: Get quest screen and modify modal panel width
        // NOTE: NO NEED CUZ modalPanel is already viewQuestPanel
//                if (this.questScreen instanceof ModalPanelsGetter modalPanelsGetter) {
//                    ModalPanel modalPanel = modalPanelsGetter.getModalPanels().getFirst();
//                    if (modalPanel != null) {
//                        modalPanel.setWidth(this.getWidth());
//                    }
//                }
        this.setPos((this.parent.width - this.width) / 2, (this.parent.height - this.height) / 2);
        int w2 = this.getWidth()/2;

//                this.panelTasks.setPosAndSize(2, 16, w2 - 3, this.panelTasks.height);
//                this.panelRewards.setPosAndSize(w2 + 2, 16, w2 - 3, this.panelRewards.height);
//
        // tasks rewards ui resize
        int bsize = 18;
//                int at = this.panelTasks.align(new CompactGridLayout(bsize + 2));
//                int ar = this.panelRewards.align(new CompactGridLayout(bsize + 2));
//                int height = Math.max(at, ar);

        this.titleField.setPosAndSize(27, 4, this.getWidth() - 54, this.titleField.height);
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

        for(Widget widgetTasks : this.panelTasks.getWidgets()) {
            widgetTasks.setX(widgetTasks.posX + tox);
            widgetTasks.setY(widgetTasks.posY + toy);
        }

        for(Widget widgetRewards : this.panelRewards.getWidgets()) {
            widgetRewards.setX(widgetRewards.posX + rox);
            widgetRewards.setY(widgetRewards.posY + roy);
        }

        for (int widgetID = 0; widgetID < this.panelContent.getWidgets().size(); widgetID++) {
            Widget contentWidget = this.panelContent.getWidget(widgetID);
            if (contentWidget instanceof TextField taskOrRewardTextField) {
                int TaskTextID = 3;
                int RewardTextID = 4;
                if (widgetID == TaskTextID) {
                    // Task text field
                    taskOrRewardTextField.setPosAndSize(2, 2, w2 - 3, 13);
                    taskOrRewardTextField.setMaxWidth(this.getWidth());
                } else if (widgetID == RewardTextID) {
                    // Reward text field
                    taskOrRewardTextField.setPosAndSize(w2 + 2, 2, w2 - 3, 13);
                    taskOrRewardTextField.setMaxWidth(this.getWidth());
                }
            }

            if (contentWidget instanceof ColorWidget boarderWidget) {
                if (widgetID == 5) {
                    boarderWidget.setPosAndSize(w2, 0, 1, 16 + height + 6);
                } else if (widgetID == 6) {
                    boarderWidget.setPosAndSize(1, 16 + height + 6, this.getWidth() - 2, 1);
                }
            }
        }

        this.panelContent.setPosAndSize(0, Math.max(16, this.titleField.height + 8), this.getWidth(), this.panelContent.height);
//                this.panelText.setHeight(this.panelText.align(new WidgetLayout.Vertical(0, 1, 2)));
//                this.setHeight(Math.min(this.panelContent.getContentHeight() + this.titleField.height + 12, this.parent.height - 10));


        // TODO: adjust everything that got width
//                this.panelTasks.setPosAndSize(2, 16, w2 - 3, this.panelTasks.height);
//                this.panelRewards.setPosAndSize(w2 + 2, 16, w2 - 3, this.panelTasks.width);

//                int at = this.panelTasks.align(new CompactGridLayout(bsize + 2));
//                int ar = this.panelRewards.align(new CompactGridLayout(bsize + 2));
//                int height = Math.max(at, ar);
//                this.panelText.setPosAndSize(3, 16 + height + 12, this.getWidth() - 6, 0);
//                this.titleField.setPosAndSize(27, 4, width - 54, this.titleField.height);
//                this.panelContent.setHeight(this.panelContent.align(new WidgetLayout.Vertical(0, 1, 2)));
        // blankpanel

        this.panelText.setHeight(this.panelText.align(new WidgetLayout.Vertical(0, 1, 2)));
        this.panelText.setPosAndSize(3, 16 + height + 12, this.getWidth() - 6, this.panelText.height);
        for (Widget textWidget : this.panelText.getWidgets()) {
            if (textWidget instanceof TextField textField) {
                textField.setMaxWidth(this.getWidth() - 6);
                textField.setWidth(this.getWidth() - 6);
            }
        }
//                this.setHeight(Math.min(this.panelContent.getContentHeight() + this.titleField.height + 12, this.parent.height - 10));
//                panelText.getWidgets().get(panelText.getWidgets().size()-1).posY =
        int iconSize = Math.min(16, this.titleField.height + 2);
        for (Widget viewWidget : this.getWidgets()) {
            if (viewWidget instanceof ICloseViewQuestButton) {
                viewWidget.setPosAndSize(this.getWidth() - iconSize - 2, 4, iconSize, iconSize);
            } else if (viewWidget instanceof IPinViewQuestButton) {
                viewWidget.setPosAndSize(this.getWidth() - iconSize * 2 - 4, 4, iconSize, iconSize);
            } else if (Objects.equals(viewWidget.getTitle(), Component.translatable("ftbquests.gui.no_dependants")) ||
                    Objects.equals(viewWidget.getTitle(), Component.translatable("ftbquests.gui.view_dependants"))) {
                viewWidget.setPosAndSize(this.getWidth() - 13, this.panelContent.posY + 2, 13, 13);
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    public void mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta, CallbackInfoReturnable<Boolean> cir) {
        if (translationLeft > 0) {
//            System.out.println("Not translated yet. Tasks left: " + translationLeft + ", cannot scroll.");
            cir.cancel();
        }
    }

    @Inject(method = "draw", at = @At("HEAD"))
    public void onDraw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!Config.ENABLE_FTB_QUEST_TRANSLATION.get()) return;
        if (Translator.getDeletingTranslationKeyHold()) {
            resetQuest();
        }

        translateTitle();

        // setup isDescriptionTranslated
        if (isDescriptionTranslated == null) {
            isDescriptionTranslated = new ArrayList<>(panelText.getWidgets().size());
            translationLeft = 0;
            for (int widgetIndex = 0; widgetIndex < panelText.getWidgets().size(); widgetIndex++) {
                if (panelText.getWidgets().get(widgetIndex) instanceof FormattedTextGetterSetter formattedTextGetterSetter) {
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
            Widget widget = panelText.getWidgets().get(widgetIndex);

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
        } else {
            setScrollY(0);
        }
    }

    @Inject(method = "onClosed", at = @At("HEAD"))
    public void onClosed(CallbackInfo ci) {
        LOGGER.debug("Warning OnClosed called, resetting translation state.");
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

    @Inject(method = "setCurrentPage", at = @At("RETURN"))
    public void onSetCurrentPage(int page, CallbackInfo ci) {
        LOGGER.debug("Warning SetCurrentPage called, resetting translation state.");
        // Reset translation state when changing pages
        isViewQuestPanelTranslated = false;
        isDescriptionTranslated = null;
        translationLeft = -1;
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
