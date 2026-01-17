package net.github.dctime.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.github.dctime.KeyMapping;
import net.github.dctime.libs.Translator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT)
public class MouseButtonEvents {
    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.KeyPressed.Post event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        if (KeyMapping.DELETE_TRANSLATION_CACHE.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            Translator.clearCache();
        }

        if (KeyMapping.SHOW_TRANSLATION_IN_GUI.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            ScreenEventRender.setShowTranslationButtonPressed(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.KeyReleased.Post event) {
        ScreenEventRender.setShowTranslationButtonPressed(false);
    }
}
