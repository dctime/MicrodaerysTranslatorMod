package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.IReorderingProcessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
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
    public static void screenEventRender(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!showTranslationButtonPressed) return;
        MatrixStack poseStack = event.getMatrixStack();
        Screen screen = event.getGui();
        List<IReorderingProcessor> list = Minecraft.getInstance().font.split(ITextProperties.of(renderText), 200);
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        screen.renderTooltip(poseStack, list, event.getMouseX(), event.getMouseY());
    }
}
