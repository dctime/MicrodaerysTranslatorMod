package net.github.dctime.events;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.Translator;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT, bus= Mod.EventBusSubscriber.Bus.FORGE)
public class RenderGuiEvent {
    // 定義你的材質位置
    private static final ResourceLocation MY_IMAGE = new ResourceLocation(
            MicrodaerysTranslatorClient.MODID,
            "textures/gui/logo.png"
    );

    public static void thinkingAnimation(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();

        // 左上角位置
        int x = 10;  // 距離左邊 10 像素
        int y = 10;  // 距離上方 10 像素

        // 圖片尺寸
        int width = 20;
        int height = 20;

        // 繪製圖片
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MY_IMAGE); // 材質位置（1.19.2 沒有 GuiGraphics，先綁定材質再 blit）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        GuiComponent.blit(
                poseStack,
                x, y,         // 螢幕位置 (x, y)
                0, 0,         // 材質上的 UV 起始位置
                width, height,// 繪製的寬高
                width, height // 材質的實際寬高
        );
        RenderSystem.disableBlend();

        int distance = 3;
        int pointWidth = 3;
        int pointHeight = 3;

        int i = (int)(Minecraft.getInstance().level.getGameTime() / 10 % 4);

        for (int pointTime = 0; pointTime < i; pointTime++) {
            GuiComponent.fill(poseStack, x+width+distance+(pointWidth+distance)*pointTime, (int)(y+width/2-pointHeight/2), x+width+(distance+pointWidth)*(pointTime+1), (int)(y+width/2-pointHeight/2)+pointHeight, 0xFFFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (!Config.ENABLE_TRANSLATING_ANIMATION_CONFIG.get()) return;
        if (Translator.isTranslating())
            thinkingAnimation(event);
    }
}
