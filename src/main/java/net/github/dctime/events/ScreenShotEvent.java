package net.github.dctime.events;

import com.mojang.blaze3d.platform.NativeImage;
import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.screen.ScreenShotSelectAreaScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenshotEvent;

import java.io.File;

@Mod.EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
