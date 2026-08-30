package net.github.dctime.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.KeyMapping;
import net.github.dctime.libs.Translator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class MouseButtonEvents {
    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.KeyPressed.Post event) {
        // In GUI
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        if (KeyMapping.DELETE_TRANSLATION_CACHE.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            Translator.clearCache();
        }

        if (KeyMapping.SHOW_TRANSLATION_IN_GUI.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            ScreenEventRender.setShowTranslationButtonPressed(true);
        }

        if (KeyMapping.DELETE_SHOWING_TRANSLATION.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            Translator.setDeletingTranslationKeyHold(true, Translator.KeyTriggeredSource.MOUSE_BUTTON_EVENT);
//            System.out.println("Mouse Button Event TRUE");
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.KeyReleased.Post event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        if (KeyMapping.SHOW_TRANSLATION_IN_GUI.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            ScreenEventRender.setShowTranslationButtonPressed(false);
        }

        if (KeyMapping.DELETE_SHOWING_TRANSLATION.get().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            Translator.setDeletingTranslationKeyHold(false, Translator.KeyTriggeredSource.MOUSE_BUTTON_EVENT);
//            System.out.println("Mouse Button Event FALSE");
        }

    }
}
