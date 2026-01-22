package net.github.dctime.mixin.ftbquests;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.matrix.MatrixStack;
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
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
public abstract class ViewQuestPanelMixin extends Panel {
    private TextField titleField;

    @Shadow(remap = false)
    private BlankPanel panelText;
    @Shadow(remap = false)
    private BlankPanel panelTasks;

    private BlankPanel panelRewards;


    @Shadow(remap = false)
    private final QuestScreen questScreen;
    @Shadow(remap = false)
    private Quest quest;
    @Shadow(remap = false)
    private BlankPanel panelContent;


//    @Shadow(remap = false)
//    private long lastScrollTime;
    private boolean isViewQuestPanelTranslated = false;
    private List<Boolean> isDescriptionTranslated = null;
    // -1 : standup, 0 : ready to resize, 1+: amount of translation left
    private int translationLeft = -1;

    private static final Logger LOGGER = LogManager.getLogger();


    private ViewQuestPanelMixin(Panel panel, QuestScreen questScreen) {
        super(panel);
        this.questScreen = questScreen;
    }



    private void resizeUI() {
        // Translation successful, do some ui adjustment
        // title width limit

        if (this.quest == null) return;

        int width = Math.max(200, this.titleField.width + 54);
        if (this.quest.minWidth > 0) {
            width = Math.max(this.quest.minWidth, width);
        }
//                else if (this.questScreen.getSelectedChapter().isPresent()) {
//                    if (this.questScreen.getSelectedChapter().get().getDefaultMinWidth() > 0) {
//                        width = Math.max(this.questScreen.getSelectedChapter().get().getDefaultMinWidth(), width);
//                    }
//                }
        this.setWidth(Math.max(width, this.width));
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
        int w2 = this.width/2;

//                this.panelTasks.setPosAndSize(2, 16, w2 - 3, this.panelTasks.height);
//                this.panelRewards.setPosAndSize(w2 + 2, 16, w2 - 3, this.panelRewards.height);
//
        // tasks rewards ui resize
        int bsize = 18;
//                int at = this.panelTasks.align(new CompactGridLayout(bsize + 2));
//                int ar = this.panelRewards.align(new CompactGridLayout(bsize + 2));
//                int height = Math.max(at, ar);

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
            if (contentWidget instanceof TextField) {
                TextField taskOrRewardTextField = (TextField) contentWidget;
                int TaskTextID = 3;
                int RewardTextID = 4;
                if (widgetID == TaskTextID) {
                    // Task text field
                    taskOrRewardTextField.setPosAndSize(2, 2, w2 - 3, 13);
                    taskOrRewardTextField.setMaxWidth(width);
                } else if (widgetID == RewardTextID) {
                    // Reward text field
                    taskOrRewardTextField.setPosAndSize(w2 + 2, 2, w2 - 3, 13);
                    taskOrRewardTextField.setMaxWidth(width);
                }
            }

            if (contentWidget instanceof ColorWidget ) {
                ColorWidget boarderWidget = (ColorWidget) contentWidget;
                if (widgetID == 5) {
                    boarderWidget.setPosAndSize(w2, 0, 1, 16 + height + 6);
                } else if (widgetID == 6) {
                    boarderWidget.setPosAndSize(1, 16 + height + 6, width - 2, 1);
                }
            }
        }

        this.panelContent.setPosAndSize(0, Math.max(16, this.titleField.height + 8), width, this.panelContent.height);
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
        this.panelText.setPosAndSize(3, 16 + height + 12, width - 6, this.panelText.height);
        for (Widget textWidget : this.panelText.widgets) {
            if (textWidget instanceof TextField) {
                TextField textField = (TextField) textWidget;
                textField.setMaxWidth(width - 6);
                textField.setWidth(width - 6);
            }
        }
//                this.setHeight(Math.min(this.panelContent.getContentHeight() + this.titleField.height + 12, this.parent.height - 10));
//                panelText.getWidgets().get(panelText.getWidgets().size()-1).posY =
        int iconSize = Math.min(16, this.titleField.height + 2);
        for (Widget viewWidget : this.widgets) {
            if (viewWidget instanceof ICloseViewQuestButton) {
                viewWidget.setPosAndSize(width - iconSize - 2, 4, iconSize, iconSize);
            } else if (viewWidget instanceof IPinViewQuestButton) {
                viewWidget.setPosAndSize(width - iconSize * 2 - 4, 4, iconSize, iconSize);
            } else if (Objects.equals(viewWidget.getTitle(), new TranslationTextComponent("ftbquests.gui.no_dependants")) ||
                    Objects.equals(viewWidget.getTitle(), new TranslationTextComponent("ftbquests.gui.view_dependants"))) {
                viewWidget.setPosAndSize(width - 13, this.panelContent.posY + 2, 13, 13);
            }
        }
    }

//    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
//    public void mouseScrolled(double scroll, CallbackInfoReturnable<Boolean> cir) {
//        if (translationLeft > 0) {
/// /            System.out.println("Not translated yet. Tasks left: " + translationLeft + ", cannot scroll.");
///
/// @return
//            cir.cancel();
//        }
//    }

    @Override
    public boolean mouseScrolled(double scroll) {
        if (translationLeft > 0) {
//            System.out.println("Not translated yet. Tasks left: " + translationLeft + ", cannot scroll.");
            return false;
        }
        return super.mouseScrolled(scroll);
    }

    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    public void onDraw(MatrixStack matrixStack, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!Config.ENABLE_FTB_QUEST_TRANSLATION.get()) return;
        if (panelText == null) return;
        translateTitle();

        // setup isDescriptionTranslated

        if (isDescriptionTranslated == null) {
            isDescriptionTranslated = new ArrayList<>(panelText.widgets.size());
            translationLeft = 0;
            for (int widgetIndex = 0; widgetIndex < panelText.widgets.size(); widgetIndex++) {
                if (panelText.widgets.get(widgetIndex) instanceof FormattedTextGetterSetter) {
                    FormattedTextGetterSetter formattedTextGetterSetter = (FormattedTextGetterSetter) panelText.widgets.get(widgetIndex);
                    isDescriptionTranslated.add(false);
                    translationLeft++;
                    LOGGER.debug("Add Translation Task, total: " + translationLeft + "Text: " + (formattedTextGetterSetter.getFormattedText().length > 0 ? formattedTextGetterSetter.getFormattedText()[0].getString() + "length: " + formattedTextGetterSetter.getFormattedText().length : "empty"));
                } else {
                    isDescriptionTranslated.add(true);
                }
            }
            return;
        }

        // send requests for all texts.
        for (int widgetIndex = 0; widgetIndex < isDescriptionTranslated.size(); widgetIndex++) {
            if (isDescriptionTranslated.get(widgetIndex)) continue;
            // not translated yet, so we need to translate it
            Widget widget = panelText.widgets.get(widgetIndex);

            if (!(widget instanceof FormattedTextGetterSetter)) return;
            FormattedTextGetterSetter formattedTextGetter = (FormattedTextGetterSetter) widget;
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

//    @Inject(method = "onClosed", at = @At("HEAD"), remap = false)
    @Override
    public void onClosed() {
        LOGGER.debug("Warning OnClosed called, resetting translation state.");
        isViewQuestPanelTranslated = false;
        isDescriptionTranslated = null;
        translationLeft = -1;
        super.onClosed();
    }

    @Override
    public void refreshWidgets() {
        super.refreshWidgets();
        LOGGER.debug("Warning RefreshWidgets called, resetting translation state.");
        isViewQuestPanelTranslated = false;
        isDescriptionTranslated = null;
        translationLeft = -1;
    }

//    @Inject(method = "setCurrentPage", at = @At("RETURN"), remap = false)
//    public void onSetCurrentPage(int page, CallbackInfo ci) {
//        LOGGER.debug("Warning SetCurrentPage called, resetting translation state.");
//        // Reset translation state when changing pages
//        isViewQuestPanelTranslated = false;
//        isDescriptionTranslated = null;
//        translationLeft = -1;
//    }

//    @Inject(method = "addWidgets", at = @At("RETURN"), remap = false)
//    public void onAddWidgets(CallbackInfo ci, @Local(name = "panelRewards") BlankPanel panelRewards) {
//        this.panelRewards = panelRewards;
//    }

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

        if (Translator.translationCache.containsKey(totalText)) {
            formattedTextGetter.setTranslatedFormattedText(Translator.translationCache.get(totalText));
            LOGGER.debug("Using cached translation for: " + totalText + " -> " + Translator.translationCache.get(totalText));

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

        if (!(titleField instanceof FormattedTextGetterSetter)) {
            LOGGER.warn("Title field is not an instance of FormattedTextGetter, cannot translate.");
            return;
        }
        FormattedTextGetterSetter formattedTextGetter = (FormattedTextGetterSetter) titleField;
        if (formattedTextGetter.getFormattedText().length < 1) {
            LOGGER.warn("Title field is Empty.");
            return;
        }

        if (translateFormattedText(formattedTextGetter)) {
            // Translation successful, set the translated flag
            isViewQuestPanelTranslated = true;
            resizeUI();
        }
    }

    @Inject(method = "addWidgets()V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0), remap = false)
    private void afterAddWidgets(CallbackInfo ci, @Local TextField titleField) {
        this.titleField = titleField;
    }
}
