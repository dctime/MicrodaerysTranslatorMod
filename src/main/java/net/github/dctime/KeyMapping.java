package net.github.dctime;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

public class KeyMapping {
    public static final Lazy<KeyBinding> DELETE_TRANSLATION_CACHE = Lazy.of(() -> new KeyBinding(
            "key."+ MicrodaerysTranslatorClient.MODID+".delete_translation_cache",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE, // Default mapping requires shift to be held down
            InputMappings.Type.KEYSYM, // Default mapping is on the keyboard
            GLFW.GLFW_KEY_F4,
            "key.categories."+ MicrodaerysTranslatorClient.MODID+".key_mapping_category"
    ));

    public static final Lazy<KeyBinding> SHOW_TRANSLATION_IN_GUI = Lazy.of(() -> new KeyBinding(
            "key."+ MicrodaerysTranslatorClient.MODID+".show_translation_in_gui",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE, // Default mapping requires shift to be held down
            InputMappings.Type.KEYSYM, // Default mapping is on the keyboard
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories."+ MicrodaerysTranslatorClient.MODID+".key_mapping_category"
    ));

    public static final Lazy<KeyBinding> DELETE_SHOWING_TRANSLATION = Lazy.of(() -> new KeyBinding(
            "key."+ MicrodaerysTranslatorClient.MODID+".delete_showing_translation",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE, // Default mapping requires shift to be held down
            InputMappings.Type.KEYSYM, // Default mapping is on the keyboard
            GLFW.GLFW_KEY_F6,
            "key.categories."+ MicrodaerysTranslatorClient.MODID+".key_mapping_category"
    ));

    // Called from MicrodaerysTranslatorClient.onClientSetup -- 1.16.5 has no RegisterKeyMappingsEvent
    // (1.16.5's KeyBinding is 1.18's KeyMapping; the class keeps its newer-branch name on purpose).
    public static void registerBindings() {
        ClientRegistry.registerKeyBinding(DELETE_TRANSLATION_CACHE.get());
        ClientRegistry.registerKeyBinding(SHOW_TRANSLATION_IN_GUI.get());
        ClientRegistry.registerKeyBinding(DELETE_SHOWING_TRANSLATION.get());
    }
}
