package net.github.dctime.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.github.dctime.events.ScreenEventRender;
import net.github.dctime.events.ScreenShotEvent;
import net.github.dctime.libs.ScreenShotter;
import net.github.dctime.libs.Translator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.IOException;
import java.util.function.IntUnaryOperator;


public class ScreenShotSelectAreaScreen extends Screen implements GuiEventListener {

    private double mouseX1;
    private double mouseX2;
    private double mouseY1;
    private double mouseY2;
    private boolean mouseDragged = false;
    private NativeImage tempImage;
    private ResourceLocation tempImageResourceLocation;
    private Screen oldScreen;
    double scaledToWindowWidthRatio;
    double scaledToWindowHeightRatio;

    public int darkenPixel(int c, float darkenImageRatio) {
        int a = (c >> 24) & 0xFF;
        int r = (int)(((c >> 16) & 0xFF) * darkenImageRatio);
        int g = (int)(((c >> 8) & 0xFF) * darkenImageRatio);
        int b = (int)((c & 0xFF) * darkenImageRatio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public ScreenShotSelectAreaScreen(NativeImage image, Screen oldScreen) {
        super(new TextComponent("screenshotselectarea"));
        System.out.println("LOAD IMAGE");
        tempImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        tempImage.copyFrom(image);
        final float darkenImageRatio = 0.66f;
        for (int i = 0; i < tempImage.getWidth(); i++) {
            for (int j = 0; j < tempImage.getHeight(); j++) {
                tempImage.setPixelRGBA(i, j, darkenPixel(tempImage.getPixelRGBA(i, j), darkenImageRatio));
            }
        }
        DynamicTexture tempImageTexture = new DynamicTexture(tempImage);
        tempImageResourceLocation = Minecraft.getInstance().getTextureManager().register("googleaistudiotranslator_screenshot_temp", tempImageTexture);
        this.oldScreen = oldScreen;
    }

    @Override
    protected void init() {
        super.init();
        mouseDragged = false;

        scaledToWindowWidthRatio = (double) Minecraft.getInstance().getWindow().getWidth()/Minecraft.getInstance().getWindow().getGuiScaledWidth();
        scaledToWindowHeightRatio = (double) Minecraft.getInstance().getWindow().getHeight()/Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        mouseX1 = mouseX >= 0 && mouseX < this.width ? mouseX : (mouseX < 0 ? 0 : this.width - 1);
        mouseY1 = mouseY >= 0 && mouseY < this.height ? mouseY : (mouseY < 0 ? 0 : this.height - 1);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mouseDragged) {
            mouseX2 = mouseX >= 0 && mouseX < this.width ? mouseX : (mouseX < 0 ? 0 : this.width - 1);
            mouseY2 = mouseY >= 0 && mouseY < this.height ? mouseY : (mouseY < 0 ? 0 : this.height - 1);
            System.out.println("Selected Area: (" + mouseX1 + ", " + mouseY1 + ") to (" + mouseX2 + ", " + mouseY2 + ")");
            processImageAndRequest();
            // Here you can add code to handle the selected area
            this.onClose();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(oldScreen);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!mouseDragged) mouseDragged = true;
        System.out.println("mouseDragged: " + mouseX + ", " + mouseY + ", " + button + ", " + dragX + ", " + dragY);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0,
                tempImageResourceLocation
        );
        GuiComponent.blit(poseStack, 0, 0, 0, 0, (int)(tempImage.getWidth()/scaledToWindowWidthRatio), (int)(tempImage.getHeight()/scaledToWindowHeightRatio), (int)(tempImage.getWidth()/scaledToWindowWidthRatio), (int)(tempImage.getHeight()/scaledToWindowHeightRatio));
        if (mouseDragged)
            GuiComponent.fill(poseStack, (int)(mouseX1), (int)(mouseY1), mouseX, mouseY, 0x80FFFFFF);
    }

    public void copyRect(NativeImage fromImage, NativeImage toImage, int pXFrom, int pYFrom, int pXToDelta, int pYToDelta, int pWidth, int pHeight, boolean pMirrorX, boolean pMirrorY) {
        for(int i = 0; i < pHeight; ++i) {
            for(int j = 0; j < pWidth; ++j) {
                int k = pMirrorX ? pWidth - 1 - j : j;
                int l = pMirrorY ? pHeight - 1 - i : i;
                int i1 = fromImage.getPixelRGBA(pXFrom + j, pYFrom + i);
//                toImage.setPixelRGBA(pXFrom + pXToDelta + k, pYFrom + pYToDelta + l, i1);
                toImage.setPixelRGBA(pXToDelta + k, pYToDelta + l, i1);
            }
        }
    }

    private void processImageAndRequest() {
        System.out.println("Gui Scaled Width: " + Minecraft.getInstance().getWindow().getGuiScaledWidth() + ", Gui Scaled Height: " + Minecraft.getInstance().getWindow().getGuiScaledHeight());
        System.out.println("Window size: w:" + Minecraft.getInstance().getWindow().getWidth() + "h: " + Minecraft.getInstance().getWindow().getHeight());

        String tempBase64Image = null;

        int startX = (int) (Math.min(mouseX1, mouseX2)*scaledToWindowWidthRatio);
        int startY = (int) (Math.min(mouseY1, mouseY2)*scaledToWindowHeightRatio);
        int endX = (int) (Math.max(mouseX1, mouseX2)*scaledToWindowWidthRatio);
        int endY = (int) (Math.max(mouseY1, mouseY2)*scaledToWindowHeightRatio);

        NativeImage cropTempImage = new NativeImage(NativeImage.Format.RGBA, endX-startX, endY-startY, false);
        copyRect(tempImage, cropTempImage, startX, startY, 0, 0, endX-startX, endY-startY, false, false);
        System.out.println("CropTempImage Size: w:" + cropTempImage.getWidth() + "h: " + cropTempImage.getHeight());
        try {
            tempBase64Image = ScreenShotter.pixelsToBase64(cropTempImage.makePixelArray(), cropTempImage.getWidth(), cropTempImage.getHeight());
        } catch (Exception e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(new TextComponent("螢幕截圖檔案無法翻成Base64" + e.getMessage()).withStyle(ChatFormatting.RED), false);
            }
//            System.out.println("Error processing image: " + e.getMessage());
//            tempBase64Image = null;
        }

        if (Translator.translating) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(new TextComponent("翻譯器正在忙碌中，請稍後再截圖。").withStyle(ChatFormatting.YELLOW), false);
                ScreenEventRender.setRenderText("翻譯器正在忙碌中，請稍後再截圖。");
                return;
            }
        }

        try {
            Translator.requestTranslateToTraditionalChinese(":", tempBase64Image, true);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(new TextComponent("螢幕截圖翻譯中...").withStyle(ChatFormatting.GREEN), false);
                ScreenEventRender.setRenderText("螢幕截圖翻譯中...");
            }
        } catch (IOException | InterruptedException e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(new TextComponent("螢幕翻譯錯誤: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            }
        }
    }
}
