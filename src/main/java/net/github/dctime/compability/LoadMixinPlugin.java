package net.github.dctime.compability;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LoadMixinPlugin implements IMixinConfigPlugin {
    private static boolean jadeLoaded = false;
    private static boolean ftbquestsLoaded = false;
    private static boolean betteradvancementsLoaded = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadMixinPlugin.class);

    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();
        LOGGER.debug("GoogleAIStudioTranslatorClient Mixin Plugin loaded.");
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (LoadingModList.get().getModFileById("jade") != null && !jadeLoaded) {
            jadeLoaded = true;
            LOGGER.info("Jaded is loaded, applying mixins for Jade compatibility.");
        }

        if (LoadingModList.get().getModFileById("ftbquests") != null && !ftbquestsLoaded) {
            ftbquestsLoaded = true;
            LOGGER.info("FTB Quests is loaded, applying mixins for FTB Quests compatibility.");
        }

        if (LoadingModList.get().getModFileById("betteradvancements") != null && !betteradvancementsLoaded) {
            betteradvancementsLoaded = true;
            LOGGER.info("Better Advancements is loaded, applying mixins for Better Advancements compatibility.");
        }

        if (mixinClassName.endsWith("ViewQuestPanelMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("TextFieldMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("QuestPanelMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("QuestDescriptionFieldMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("PinViewQuestButtonMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("CloseViewQuestButtonMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("BaseScreenMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("AdvancementWidgetMixin")) return true;
        if (mixinClassName.endsWith("BetterAdvancementWidgetMixin")) return betteradvancementsLoaded;
//        if (mixinClassName.endsWith("ItemStackElementMixin")) return jadeLoaded;
        if (mixinClassName.endsWith("TooltipRendererMixin")) return jadeLoaded;
        if (mixinClassName.endsWith("TooltipLineMixin")) return jadeLoaded;
        if (mixinClassName.endsWith("TooltipMixin")) return jadeLoaded;
        if (mixinClassName.endsWith("OverlayRendererMixin")) return jadeLoaded;
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return new ArrayList<>();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
