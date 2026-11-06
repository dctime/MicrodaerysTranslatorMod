package net.github.dctime.compability;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LoadMixinPlugin implements IMixinConfigPlugin {
    private static boolean jadeLoaded = false;
    private static boolean ftbquestsLoaded = false;
    private static boolean betteradvancementsLoaded = false;
    private static final Logger LOGGER = LogManager.getLogger(LoadMixinPlugin.class);

    @Override
    public void onLoad(String mixinPackage) {
        // 1.16.5: MixinExtras is shaded into the jar (Forge 36 has no jarJar to ship
        // mixinextras-forge, which bootstraps itself on 1.18.2+), so it has to be initialized by
        // hand before any @Local/@ModifyExpressionValue injector in this config is applied.
        MixinExtrasBootstrap.init();
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (LoadingModList.get().getModFileById("jade") != null && !jadeLoaded) {
            jadeLoaded = true;
            LOGGER.info("Jade is loaded, applying mixins for Jade compatibility.");
        }

        if (LoadingModList.get().getModFileById("ftbquests") != null && !ftbquestsLoaded) {
            ftbquestsLoaded = true;
            LOGGER.info("FTB Quests is loaded, applying mixins for FTB Quests compatibility.");
        }

        // E6: was "&& !ftbquestsLoaded" (copy-paste) -- with FTB Quests also installed, the block
        // above had just set that true, so betteradvancementsLoaded could never become true.
        if (LoadingModList.get().getModFileById("betteradvancements") != null && !betteradvancementsLoaded) {
            betteradvancementsLoaded = true;
            LOGGER.info("Better Advancements is loaded, applying mixins for Better Advancements compatibility.");
        }

        if (mixinClassName.endsWith("ViewQuestPanelMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("TextFieldMixin")) return ftbquestsLoaded;
        if (mixinClassName.endsWith("QuestPanelMixin")) return ftbquestsLoaded;
        // E6: the leading '.' matters -- a bare endsWith("AdvancementWidgetMixin") also matched
        // BetterAdvancementWidgetMixin and returned true before its own gate below ever ran, so it
        // was applied even without Better Advancements installed (and the line above was masked).
        if (mixinClassName.endsWith(".AdvancementWidgetMixin")) return true;
        if (mixinClassName.endsWith("BetterAdvancementWidgetMixin")) return betteradvancementsLoaded;
        if (mixinClassName.endsWith("ScreenTooltipMixin")) return true; // vanilla target, always present (#5; 1.19.2 form of GuiGraphicsTooltipMixin)
        // 1.18.2/1.16.5: Jade 5 / Jade 2 (Hwyla fork) have no JadeTooltipCollectedCallback, so the
        // overlay is hooked directly.
        // Full ".jade." names on purpose -- a bare endsWith("TooltipMixin") would also match
        // ScreenTooltipMixin (same E6-style shadowing bug as the advancement pair above).
        // 1.16.5: this one actually targets JEI's mezz.jei.gui.TooltipRenderer (a leftover no-op
        // from the 1.18.2 -> 1.16.5 port), so it also needs JEI present, not just Jade.
        if (mixinClassName.endsWith(".jade.TooltipRendererMixin")) return jadeLoaded && LoadingModList.get().getModFileById("jei") != null;
        if (mixinClassName.endsWith(".jade.TooltipLineMixin")) return jadeLoaded;
        if (mixinClassName.endsWith(".jade.TooltipMixin")) return jadeLoaded;
        if (mixinClassName.endsWith(".jade.OverlayRendererMixin")) return jadeLoaded;
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
