package net.github.dctime;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyBindingMap;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static net.minecraft.client.util.InputMappings.Type.KEYSYM;

//@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyMapping {
    public static final Lazy<KeyBinding> DELETE_TRANSLATION_CACHE = Lazy.of(() -> new KeyBinding(
            "key."+GoogleAIStudioTranslatorClient.MODID+".delete_translation_cache",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE, // Default mapping requires shift to be held down
            KEYSYM, // Default mapping is on the keyboard
            GLFW.GLFW_KEY_F4,
            "key.categories."+GoogleAIStudioTranslatorClient.MODID+".key_mapping_category"
    ));

    public static final Lazy<KeyBinding> SHOW_TRANSLATION_IN_GUI = Lazy.of(() -> new KeyBinding(
            "key."+GoogleAIStudioTranslatorClient.MODID+".show_translation_in_gui",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE, // Default mapping requires shift to be held down
            KEYSYM, // Default mapping is on the keyboard
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories."+GoogleAIStudioTranslatorClient.MODID+".key_mapping_category"
    ));

    public static void registerBindings() {
        ClientRegistry.registerKeyBinding(DELETE_TRANSLATION_CACHE.get());
        ClientRegistry.registerKeyBinding(SHOW_TRANSLATION_IN_GUI.get());
    }
}
