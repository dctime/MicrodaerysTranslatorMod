package net.github.dctime.libs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

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
}
