package net.github.dctime.events;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
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
    public static void screenEventRender(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!showTranslationButtonPressed) return;
        MatrixStack poseStack = event.getMatrixStack();
        Screen screen = event.getGui();
        List<? extends IReorderingProcessor> list = Minecraft.getInstance().font.split(new StringTextComponent(renderText), 200);
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        screen.renderTooltip(poseStack, list, event.getMouseX(), event.getMouseY());
    }
}
