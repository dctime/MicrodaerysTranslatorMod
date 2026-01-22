package net.github.dctime.libs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.architectury.event.events.TickEvent;
import net.github.dctime.Config;
import net.github.dctime.events.ScreenEventRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static net.github.dctime.libs.ScreenShotter.getItemStackImage;

public class Translator {
    public static HashMap<String, String> translationCache = new HashMap<>();
    public static boolean translating = false;
//    private static final HttpClient CLIENT = HttpClient.newBuilder()
//            .connectTimeout(Duration.ofSeconds(10))
//            .build();

//    private static final CloseableHttpClient CLIENT = HttpClients.custom()
//            .setDefaultRequestConfig(RequestConfig.custom()
//                    .setConnectTimeout(10000) // 10秒
//                    .setConnectionRequestTimeout(10000)
//                    .setSocketTimeout(10000)
//                    .build())
//            .build();

    private static final CloseableHttpClient CLIENT = HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(10000) // 10秒
                    .setConnectionRequestTimeout(5000)
                    .setSocketTimeout(30000)
                    .build())
            .build();
    private static boolean hasShowConnectionError = false;
    private static boolean hasShowAPIKEYError = false;
    private static boolean hasShowRequestTooFrequentError = false;
    private static boolean hasShowOtherError = false;
    private static final Logger LOGGER = LogManager.getLogger();
    // --- ftb quest ---

    public static final Style translatedStyle = Style.EMPTY.withColor(TextFormatting.GRAY);

    public static void clearCache() {
        // Execute logic to perform on click here
        if (Translator.translationCache.isEmpty()) return;
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) return;
        Translator.translationCache.clear();
        player.displayClientMessage(new StringTextComponent("Translation cache cleared.").withStyle(TextFormatting.YELLOW), false);
        player.displayClientMessage(new StringTextComponent("清除翻譯快取").withStyle(TextFormatting.YELLOW), false);
    }

    private static HttpPost setupRequest(String textInEnglish, @Nullable String image, boolean isScreenShot) {
//        String model = "gemma-3-27b-it";
        String model = Config.MODEL_NAME.get();
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", model);
//
        String prompt = Config.PROMPT.get() + "\n" + textInEnglish;
        if (isScreenShot) {
            prompt = Config.PROMPT_SCREENSHOT.get();
        }

        String jsonBody = getJsonBody(image, prompt);

        String apiKey = Config.API_KEY.get();
//        if (apiKey.isBlank()) return null; // TODO:

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json; charset=utf-8");
        post.setHeader("x-goog-api-key", apiKey);
        StringEntity entity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
        post.setEntity(entity);
//        HttpRequest req = HttpRequest.newBuilder()
//                .uri(URI.create(url))
//                .timeout(Duration.ofSeconds(30))
//                .header("Content-Type", "application/json; charset=utf-8")
//                .header("x-goog-api-key", apiKey) // 可以用 ?key=... 也行
//                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
//                .build();
//
        if (StringUtils.isBlank(textInEnglish)) {
            translationCache.put(textInEnglish, "");
            return null;
        }

        return post;
    }

    private static String getJsonBody(String image, String prompt) {
//        String jsonBody;
//        if (image == null) {
//            jsonBody = "{\"contents\": [{ \"parts\": [{ \"text\": \"" + prompt + "\"}]}]}";
//        } else {
//            jsonBody = "{\"contents\": [{ \"parts\": [{ \"text\": \"" + prompt + "\"},{ \"inline_data\": {\"mime_type\": \"image/png\",\"data\":\"" + image + "\"}}]}]}";
//        }
//        System.out.println("Decoder test:\n" + jsonBody);

//        return jsonBody;
        // Adding generationConfig to ensure randomness
        String config = ",\"generationConfig\": {\"temperature\": 1.5, \"topP\": 0.95, \"topK\": 40}";

        if (image == null) {
            return "{\"contents\": [{ \"parts\": [{ \"text\": \"" + prompt + "\"}]}]" + config + "}";
        } else {
            return "{\"contents\": [{ \"parts\": [{ \"text\": \"" + prompt + "\"},{ \"inline_data\": {\"mime_type\": \"image/png\",\"data\":\"" + image + "\"}}]}]" + config + "}";
        }
    }

    private static boolean containsChinese(String str) {
        if (str == null) {
            return false;
        }
        // 检查是否包含至少一个中文字符
        return Pattern.compile("[\u4e00-\u9fa5]").matcher(str).find();
    }

    public static void requestTranslateToTraditionalChinese(String textInEnglish) throws IOException, InterruptedException {
        requestTranslateToTraditionalChinese(textInEnglish, null, false);
    }

    public static void requestTranslateItemStackToTraditionalChinese(String textInEnglish, ItemStack stack) throws IOException, InterruptedException {
        if (stack != null && !translating && Config.ENABLE_ICON_CONFIG.get()) {
            RenderSystem.recordRenderCall(() -> {
                String image = getItemStackImage(stack);
                try {
                    requestTranslateToTraditionalChinese(textInEnglish, image, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            requestTranslateToTraditionalChinese(textInEnglish);
        }

    }

    public static void requestTranslateToTraditionalChinese(String textInEnglish, String image, boolean isScreenShot) throws IOException, InterruptedException {
        if (translating) {
            // System.out.println("Translator in use.");
            return;
        }

        String textInEnglishFixed = textInEnglish.replace("\"", "\\\"");
//        System.out.println("TextInEnglishFixed: " + textInEnglishFixed);
        HttpPost post = setupRequest(textInEnglishFixed, image, isScreenShot);
        if (post == null) {
            LOGGER.warn("HTTP request is NULL.");
            return;
        }

        if (containsChinese(textInEnglishFixed)) {
            translationCache.put(textInEnglishFixed, "");
            LOGGER.debug("Text contains Chinese, skipping translation: " + textInEnglishFixed);
            return;
        }

        translating = true;

        System.out.println("Sending post");

        CompletableFuture.supplyAsync(()->{
            try (CloseableHttpResponse response = CLIENT.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return new Response(statusCode, body);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((resp, throwable) -> {
            try {
                if (throwable != null) {
                    LOGGER.warn("Translation request failed: " + throwable.getMessage());
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null && !hasShowConnectionError) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    new StringTextComponent("Translate failed! Check Your Internet Connection").withStyle(TextFormatting.YELLOW), false);
                            Minecraft.getInstance().player.displayClientMessage(
                                    new StringTextComponent("無法翻譯! 請檢查網路連線").withStyle(TextFormatting.YELLOW), false);
                            hasShowConnectionError = true;
                        }
                    });
                    return;
                }
                hasShowConnectionError = false;
                String responseText = resp.body();
                Gson gson = new Gson();
                JsonObject response = gson.fromJson(responseText, JsonObject.class);
                String translatedText = null;

                try {
                    translatedText = response.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();
                } catch (Exception e) {
                    LOGGER.warn("Error parsing response: " + responseText);
                    if (resp.statusCode() == 403) {
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null && !hasShowAPIKEYError) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("Translation failed! Check Your Google AI Studio Key in config!").withStyle(TextFormatting.YELLOW), false);
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("無法翻譯! 請檢查你的 config 資料夾的Google Ai Studio 之 API KEY").withStyle(TextFormatting.YELLOW), false);
                                hasShowAPIKEYError = true;
                            }
                        });
                    } else if (resp.statusCode() == 429) {
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null && !hasShowRequestTooFrequentError) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("Translation failed! You request too frequently and exceed your current quota (RPM)").withStyle(TextFormatting.YELLOW), false);
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("無法翻譯! 你請求的速度過快導致超過你的方案的RPM 請稍後在試").withStyle(TextFormatting.YELLOW), false);
                                hasShowRequestTooFrequentError = true;
                            }
                        });
                    } else if (!hasShowOtherError) {
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("Translation failed! HTTP Status Code: " + resp.statusCode()).withStyle(TextFormatting.RED), false);
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("翻譯失敗! HTTP 回傳碼: " + resp.statusCode()).withStyle(TextFormatting.RED), false);
                                hasShowOtherError = true;
                            }
                        });
                    }
                    return;
                }
                hasShowRequestTooFrequentError = false;
                hasShowAPIKEYError = false;
                hasShowOtherError = false;

                if (translatedText != null) {
                    translatedText = translatedText
                            .replace("\n", " ")
                            .replaceAll("\\p{Cntrl}", "")
                            .trim();
                    if (!isScreenShot) {
                        translationCache.put(textInEnglish, translatedText);
                        LOGGER.debug("Translated: " + textInEnglishFixed + " -> " + translatedText);
                    } else {
                        final String finalTranslatedText = translatedText;
                        Minecraft.getInstance().execute(()->{
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        new StringTextComponent("螢幕翻譯結果: \n" + finalTranslatedText).withStyle(Translator.translatedStyle), false
                                );
                                sendDataToScreen(finalTranslatedText);
                            }
                        });
                    }
                }

                LOGGER.debug("status: " + resp.statusCode());
            } finally {
                translating = false;
            }
        });
    }

    private static void sendDataToScreen(String finalTranslatedText) {

        ScreenEventRender.setRenderText(finalTranslatedText);
    }
}
