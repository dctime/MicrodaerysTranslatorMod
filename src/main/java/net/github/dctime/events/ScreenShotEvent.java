package net.github.dctime.events;

import net.github.dctime.Config;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.github.dctime.libs.Translator;
import net.github.dctime.screen.ScreenShotSelectAreaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenshotEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ScreenShotEvent {
    @SubscribeEvent
    public static void onScreenShot(ScreenshotEvent event) {
        if (!Config.ENABLE_SCREENSHOT_CONFIG.get()) return;
        final NativeImage image = event.getImage();
        File file = event.getScreenshotFile();

        Minecraft.getInstance().setScreen(new ScreenShotSelectAreaScreen(image, Minecraft.getInstance().screen));
//        System.out.println("SCREEN SHOT!");



//        if (lastScreenShotImage != null) {
//            System.out.println("Image:\n" + lastScreenShotImage);
//        }



    }


}
