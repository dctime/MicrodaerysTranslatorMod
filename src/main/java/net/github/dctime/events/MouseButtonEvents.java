package net.github.dctime.events;

import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.github.dctime.KeyMapping;
import net.github.dctime.libs.Translator;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyBindingMap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT)
public class MouseButtonEvents {
    @SubscribeEvent
    public static void onMouseButtonPressed(GuiScreenEvent.KeyboardKeyPressedEvent.Post event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        if (KeyMapping.DELETE_TRANSLATION_CACHE.get().isActiveAndMatches(InputMappings.getKey(keyCode, scanCode))) {
            Translator.clearCache();
        }

        if (KeyMapping.SHOW_TRANSLATION_IN_GUI.get().isActiveAndMatches(InputMappings.getKey(keyCode, scanCode))) {
            ScreenEventRender.setShowTranslationButtonPressed(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(GuiScreenEvent.KeyboardKeyReleasedEvent.Post event) {
        ScreenEventRender.setShowTranslationButtonPressed(false);
    }
}
