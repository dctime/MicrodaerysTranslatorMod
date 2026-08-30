package net.github.dctime.libs;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static net.minecraft.client.Minecraft.ON_OSX;

public class ScreenShotter {
    public static String pixelsToBase64(int[] pixels, int width, int height) throws Exception {
        // 建立 BufferedImage (ARGB 格式)
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] imageData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // 複製你的 RGBA 資料進去
        System.arraycopy(pixels, 0, imageData, 0, pixels.length);

        // 轉成 PNG (你也可以換成 "jpg")
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        // Base64 編碼
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private static void renderItem(ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            PoseStack pose = new PoseStack();
            BakedModel bakedmodel = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
            pose.pushPose();
            pose.translate((float)(x + 8), (float)(y + 8), (float)(150 + (bakedmodel.isGui3d() ? 0 : 0)));

            try {
                pose.scale(16.0F, -16.0F, 16.0F);
                boolean flag = !bakedmodel.usesBlockLight();
                if (flag) {
                    Lighting.setupForFlatItems();
                }

                Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, Minecraft.getInstance().renderBuffers().bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, bakedmodel);

                RenderSystem.disableDepthTest();
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                RenderSystem.enableDepthTest();

                if (flag) {
                    Lighting.setupFor3DItems();
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering item");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being rendered");
                crashreportcategory.setDetail("Item Type", () -> String.valueOf(stack.getItem()));
                crashreportcategory.setDetail("Item Components", () -> String.valueOf(stack.getComponents()));
                crashreportcategory.setDetail("Item Foil", () -> String.valueOf(stack.hasFoil()));
                throw new ReportedException(crashreport);
            }

            pose.popPose();
        }
    }

    public static String getItemStackImage(ItemStack stack) {
        TextureTarget target = new TextureTarget(64, 64, false, ON_OSX);

        target.bindWrite(true);

        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0, 16, 16, 0, -1000, 1000),
                VertexSorting.ORTHOGRAPHIC_Z
        );

        renderItem(stack, 0, 0);

        NativeImage image = Screenshot.takeScreenshot(target);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        convertBGRAtoRGBA(image);
        try {
            String stringImage = ScreenShotter.pixelsToBase64(image.getPixelsRGBA(), image.getWidth(), image.getHeight());
            return stringImage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static void convertBGRAtoRGBA(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = img.getPixelRGBA(x, y);

                int a = (c >> 24) & 0xFF;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8)  & 0xFF;
                int b = c & 0xFF;

                // swap R <-> B
                img.setPixelRGBA(
                        x, y,
                        (a << 24) | (b << 16) | (g << 8) | r
                );
            }
        }
    }
}
