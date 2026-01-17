package net.github.dctime.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT)
public class ScreenEventRender {
    private static boolean showTranslationButtonPressed = false;
    public static void setShowTranslationButtonPressed(boolean value) {
        showTranslationButtonPressed = value;
    }

    public static void setRenderText(String text) {
        renderText = text;
    }

    public static String renderText = "未截圖過請先截圖";

    @SubscribeEvent
    public static void screenEventRender(ScreenEvent.Render.Post event) {
        if (!showTranslationButtonPressed) return;
        PoseStack poseStack = event.getPoseStack();
        Screen screen = event.getScreen();
        List<FormattedCharSequence> list = Minecraft.getInstance().font.split(FormattedText.of(renderText), 200);
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        screen.renderTooltip(poseStack, list, event.getMouseX(), event.getMouseY(), Minecraft.getInstance().font);
    }
}
