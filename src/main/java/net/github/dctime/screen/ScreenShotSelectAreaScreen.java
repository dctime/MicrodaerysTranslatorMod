package net.github.dctime.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.github.dctime.events.ScreenEventRender;
import net.github.dctime.events.ScreenShotEvent;
import net.github.dctime.libs.ScreenShotter;
import net.github.dctime.libs.Translator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
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

    public ScreenShotSelectAreaScreen(NativeImage image, Screen oldScreen) {
        super(Component.literal("screenshotselectarea"));
        System.out.println("LOAD IMAGE");
        tempImage = image.mappedCopy(IntUnaryOperator.identity());
        final float darkenImageRatio = 0.66f;
        DynamicTexture tempImageTexture = new DynamicTexture(tempImage.mappedCopy(c -> {
            int a = (c >> 24) & 0xFF;
            int r = (int)(((c >> 16) & 0xFF) * darkenImageRatio);
            int g = (int)(((c >> 8) & 0xFF) * darkenImageRatio);
            int b = (int)((c & 0xFF) * darkenImageRatio);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }));
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(tempImageResourceLocation, 0, 0, 0, 0, (int)(tempImage.getWidth()/scaledToWindowWidthRatio), (int)(tempImage.getHeight()/scaledToWindowHeightRatio), (int)(tempImage.getWidth()/scaledToWindowWidthRatio), (int)(tempImage.getHeight()/scaledToWindowHeightRatio));
        if (mouseDragged)
            guiGraphics.fill((int)(mouseX1), (int)(mouseY1), mouseX, mouseY, 0x80FFFFFF);
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
        tempImage.copyRect(cropTempImage, startX, startY, 0, 0, endX-startX, endY-startY, false, false);
        System.out.println("CropTempImage Size: w:" + cropTempImage.getWidth() + "h: " + cropTempImage.getHeight());
        try {
            tempBase64Image = ScreenShotter.pixelsToBase64(cropTempImage.getPixelsRGBA(), cropTempImage.getWidth(), cropTempImage.getHeight());
        } catch (Exception e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("螢幕截圖檔案無法翻成Base64" + e.getMessage()).withStyle(ChatFormatting.RED));
            }
//            System.out.println("Error processing image: " + e.getMessage());
//            tempBase64Image = null;
        }

        if (Translator.translating) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("翻譯器正在忙碌中，請稍後再截圖。").withStyle(ChatFormatting.YELLOW));
                ScreenEventRender.setRenderText("翻譯器正在忙碌中，請稍後再截圖。");
                return;
            }
        }

        try {
            Translator.requestTranslateToTraditionalChinese(":", tempBase64Image);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("螢幕截圖翻譯中...").withStyle(ChatFormatting.GREEN));
                ScreenEventRender.setRenderText("螢幕截圖翻譯中...");
            }
        } catch (IOException | InterruptedException e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("螢幕翻譯錯誤: " + e.getMessage()).withStyle(ChatFormatting.RED));
            }
        }
    }
}
