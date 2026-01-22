package net.github.dctime.mixin.ftbquests;

import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.TextField;
import net.github.dctime.libs.ftbquests.QuestDescriptionFieldGetterSetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftbquests.gui.quests.ViewQuestPanel$QuestDescriptionField")
public abstract class QuestDescriptionFieldMixin extends TextField implements QuestDescriptionFieldGetterSetter {
    public QuestDescriptionFieldMixin(Panel panel) {
        super(panel);
    }

//    @Inject(method = "<init>*", at = @At("RETURN"))
//    private void onInit(CallbackInfo ci) {
////        System.out.println("QuestDescriptionField mixin applied!");
//    }
}
