package net.github.dctime.events;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT, bus= EventBusSubscriber.Bus.GAME)
public class RenderGuiEvent {
    // 定義你的材質位置
    private static final ResourceLocation MY_IMAGE = ResourceLocation.fromNamespaceAndPath(
            MicrodaerysTranslatorClient.MODID,
            "textures/gui/logo.png"
    );

    public static void thinkingAnimation(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // 左上角位置
        int x = 10;  // 距離左邊 10 像素
        int y = 10;  // 距離上方 10 像素

        // 圖片尺寸
        int width = 20;
        int height = 20;

        // 繪製圖片
        guiGraphics.blit(
                MY_IMAGE,     // 材質位置
                x, y,         // 螢幕位置 (x, y)
                0, 0,         // 材質上的 UV 起始位置
                width, height,// 繪製的寬高
                width, height // 材質的實際寬高
        );

        int distance = 3;
        int pointWidth = 3;
        int pointHeight = 3;

        int i = (int)(Minecraft.getInstance().level.getGameTime() / 10 % 4);

        for (int pointTime = 0; pointTime < i; pointTime++) {
            guiGraphics.fill(x+width+distance+(pointWidth+distance)*pointTime, (int)(y+width/2-pointHeight/2), x+width+(distance+pointWidth)*(pointTime+1), (int)(y+width/2-pointHeight/2)+pointHeight, 0xFFFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        if (!Config.ENABLE_TRANSLATING_ANIMATION_CONFIG.get()) return;
        if (Translator.isTranslating())
            thinkingAnimation(event);
    }
}
