package net.github.dctime.libs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.github.dctime.Config;
import net.github.dctime.events.ScreenEventRender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.regex.Pattern;

import static net.github.dctime.libs.ScreenShotter.getItemStackImage;

public class Translator {
    public static HashMap<String, String> translationCache = new HashMap<>();
    public static boolean translating = false;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static boolean hasShowConnectionError = false;
    private static boolean hasShowAPIKEYError = false;
    private static boolean hasShowRequestTooFrequentError = false;
    private static boolean hasShowOtherError = false;
    private static Logger LOGGER = LoggerFactory.getLogger(Translator.class);
    // --- ftb quest ---

    public enum KeyTriggeredSource {
        MOUSE_BUTTON_EVENT,
        CLIENT_TICK
    }

    private static boolean deletingTranslationKeyHold = false;
    private static KeyTriggeredSource deletingTranslationSource = null;

    public static boolean getDeletingTranslationKeyHold() {
        return deletingTranslationKeyHold;
    }

    public static void setDeletingTranslationKeyHold(boolean value, KeyTriggeredSource src) {
        if (!value && src != deletingTranslationSource) return; // two sources spamming false
        if (!deletingTranslationKeyHold && value) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            player.sendSystemMessage(Component.literal("Cleared Displayed Translations").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal("重新翻譯目前顯示的翻譯").withStyle(ChatFormatting.YELLOW));
        }
        deletingTranslationSource = src;
        deletingTranslationKeyHold = value;
    }

    public static final Style translatedStyle = Style.EMPTY.withColor(ChatFormatting.GRAY);

    public static boolean textInCache(String text) {
        if (deletingTranslationKeyHold) {
            translationCache.remove(text);
//            System.out.println("REMOVE TRANSLATION: " + text);
            return false;
        }

//        System.out.println("FINDING TRNASLATION IN CACHE: " + text + " deleting: " + deletingTranslationKeyHold);
        return translationCache.containsKey(text);
    }

    public static String getTranslationFromCache(String key) {
        return translationCache.get(key);
    }

    public static void clearCache() {
        // Execute logic to perform on click here
        if (Translator.translationCache.isEmpty()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        Translator.translationCache.clear();
        player.sendSystemMessage(Component.literal("Translation cache cleared.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("清除翻譯快取").withStyle(ChatFormatting.YELLOW));
    }

        private static HttpRequest setupRequest(String textInEnglish, @Nullable String image, boolean isScreenShot) {
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

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("x-goog-api-key", apiKey) // 可以用 ?key=... 也行
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
    //
            if (textInEnglish.isBlank()) {
                translationCache.put(textInEnglish, "");
                return null;
            }

            return req;
        }

    private static String getJsonBody(String image, String prompt) {
        String jsonBody;
        if (image == null) {
            jsonBody = """
                {
                  "contents": [
                    { "parts": [
                     { "text": "
                """ + prompt + """
                "}
                ]}
                  ]
                }
                """;
        } else {
            jsonBody = """
                    {
                      "contents": [
                        { "parts": [
                         { "text": "
                    """ + prompt + "\"},{ \"inline_data\": {\"mime_type\": \"image/png\",\"data\":\"" + image + "\"}}]}]}";
        }
//        System.out.println("Decoder test:\n" + jsonBody);

        return jsonBody;
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
        HttpRequest req = setupRequest(textInEnglishFixed, image, isScreenShot);
        if (req == null) {
            LOGGER.warn("HTTP request is NULL.");
            return;
        }

        if (containsChinese(textInEnglishFixed)) {
            translationCache.put(textInEnglishFixed, "");
            LOGGER.debug("Text contains Chinese, skipping translation: " + textInEnglishFixed);
            return;
        }

        translating = true;

        CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, throwable) -> {
                    try {
                        if (throwable != null) {
                            LOGGER.warn("Translation request failed: " + throwable.getMessage());
                            Minecraft.getInstance().execute(() -> {
                                if (Minecraft.getInstance().player != null && !hasShowConnectionError) {
                                    Minecraft.getInstance().player.sendSystemMessage(
                                            Component.literal("Translate failed! Check Your Internet Connection").withStyle(ChatFormatting.YELLOW));
                                    Minecraft.getInstance().player.sendSystemMessage(
                                            Component.literal("無法翻譯! 請檢查網路連線").withStyle(ChatFormatting.YELLOW));
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
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("Translation failed! Check Your Google AI Studio Key in config!").withStyle(ChatFormatting.YELLOW));
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("無法翻譯! 請檢查你的 config 資料夾的Google Ai Studio 之 API KEY").withStyle(ChatFormatting.YELLOW));
                                        hasShowAPIKEYError = true;
                                    }
                                });
                            } else if (resp.statusCode() == 429) {
                                Minecraft.getInstance().execute(() -> {
                                    if (Minecraft.getInstance().player != null && !hasShowRequestTooFrequentError) {
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("Translation failed! You request too frequently and exceed your current quota (RPM)").withStyle(ChatFormatting.YELLOW));
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("無法翻譯! 你請求的速度過快導致超過你的方案的RPM 請稍後在試").withStyle(ChatFormatting.YELLOW));
                                        hasShowRequestTooFrequentError = true;
                                    }
                                });
                            } else if (!hasShowOtherError) {
                                Minecraft.getInstance().execute(() -> {
                                    if (Minecraft.getInstance().player != null) {
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("Translation failed! HTTP Status Code: " + resp.statusCode()).withStyle(ChatFormatting.RED));
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("翻譯失敗! HTTP 回傳碼: " + resp.statusCode()).withStyle(ChatFormatting.RED));
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
                                        Minecraft.getInstance().player.sendSystemMessage(
                                                Component.literal("螢幕翻譯結果: \n" + finalTranslatedText).withStyle(Translator.translatedStyle)
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
