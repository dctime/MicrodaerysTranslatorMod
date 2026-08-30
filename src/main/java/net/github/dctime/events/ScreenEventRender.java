package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
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
        GuiGraphics graphics = event.getGuiGraphics();
        List<FormattedCharSequence> list = Minecraft.getInstance().font.split(FormattedText.of(renderText), 200);
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        graphics.renderTooltip(Minecraft.getInstance().font, list, event.getMouseX(), event.getMouseY());
    }
}
