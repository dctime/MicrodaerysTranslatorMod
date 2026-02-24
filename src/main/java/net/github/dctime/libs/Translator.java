package net.github.dctime.libs;

import com.google.gson.*;
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
import java.util.function.BooleanSupplier;
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
                .timeout(Duration.ofSeconds(Config.TIMEOUT_DURATION_CONFIG.get()))
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

    public static HttpRequest setupRequestOllama(String textInEnglish,
                                                 @Nullable String imageBase64,
                                                 boolean isScreenShot) {

        if (textInEnglish == null || textInEnglish.isBlank()) {
            return null;
        }

        String prompt = Config.PROMPT.get() + "\n" + textInEnglish;

        if (isScreenShot) {
            prompt = Config.PROMPT_SCREENSHOT.get() + "\n" + textInEnglish;
        }

        String model = Config.MODEL_NAME.get(); // 例如 "phi3"

        String url = "http://127.0.0.1:11434/api/generate";

        String jsonBody = buildOllamaJson(prompt, imageBase64, model);

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Config.TIMEOUT_DURATION_CONFIG.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    public static HttpRequest setupRequestMistral(String textInEnglish,
                                                 @Nullable String imageBase64,
                                                 boolean isScreenShot) {

        if (textInEnglish == null || textInEnglish.isBlank()) {
            return null;
        }

        String prompt = Config.PROMPT.get() + "\n" + textInEnglish;

        if (isScreenShot) {
            prompt = Config.PROMPT_SCREENSHOT.get() + "\n" + textInEnglish;
        }

        String model = Config.MODEL_NAME.get();

        String url = "https://api.mistral.ai/v1/chat/completions";

        String jsonBody = buildMistralJson(prompt, imageBase64, model);

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Config.TIMEOUT_DURATION_CONFIG.get()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + Config.API_KEY.get())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private static String buildOllamaJson(String prompt,
                                          @Nullable String imageBase64,
                                          String model) {

        String safePrompt = escapeJson(prompt);

        if (imageBase64 == null) {
            return """
                {
                  "model": "%s",
                  "prompt": "%s",
                  "stream": false
                }
                """.formatted(model, safePrompt);
        } else {

            // Ollama 圖片要用 images 陣列
            return """
                {
                  "model": "%s",
                  "prompt": "%s",
                  "images": ["%s"],
                  "stream": false
                }
                """.formatted(model, safePrompt, imageBase64);
        }
    }

    private static String buildMistralJson(String prompt,
                                          @Nullable String imageBase64,
                                          String model) {

        String safePrompt = escapeJson(prompt);

        if (imageBase64 == null) {
            return """
        {
          "model": "%s",
          "messages": [
            {
              "role": "user",
              "content": "%s"
            }
          ]
        }
        """.formatted(model, safePrompt);
        } else {
            return """
        {
          "model": "%s",
          "messages": [
            {
              "role": "user",
              "content": [
                {
                  "type": "text",
                  "text": "%s"
                },
                {
                  "type": "image_url",
                  "image_url": "data:image/png;base64,%s"
                }
              ]
            }
          ]
        }
        """.formatted(model, safePrompt, imageBase64);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7E) { // 非 ASCII 控制字元
                        sb.append(String.format("\\u%04x", (int)c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
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

    public static void requestTranslateToTraditionalChinese(String textInEnglish,
                                                            String image,
                                                            boolean isScreenShot)
            throws IOException, InterruptedException {

        if (translating) return;

        String fixedText = textInEnglish.replace("\"", "\\\"");

        if (containsChinese(fixedText)) {
            translationCache.put(fixedText, "");
            LOGGER.debug("Text contains Chinese, skipping translation: " + fixedText);
            return;
        }

        HttpRequest request;
        if (Config.ENDPOINT_CONFIG.get() == Config.EndPoint.OLLAMA)
            request = setupRequestOllama(fixedText, image, isScreenShot);
        else if (Config.ENDPOINT_CONFIG.get() == Config.EndPoint.MISTRAL)
            request = setupRequestMistral(fixedText, image, isScreenShot);
        else
            request = setupRequest(fixedText, image, isScreenShot);

        if (request == null) {
            LOGGER.warn("HTTP request is NULL.");
            return;
        }

        translating = true;

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, throwable) -> {
                    try {
                        if (throwable != null) {
                            handleConnectionError(throwable);
                            return;
                        }

                        handleHttpResponse(resp, fixedText, textInEnglish, isScreenShot);

                    } finally {
                        translating = false;
                    }
                });
    }

    private static void handleHttpResponse(HttpResponse<String> resp,
                                           String fixedText,
                                           String originalText,
                                           boolean isScreenShot) {

        String responseText = resp.body();
        String translatedText;

        try {
            if (isOllamaResponse(responseText)) {
                translatedText = parseOllamaResponse(responseText);
            } else if (isMistralResponse(responseText)) {
                translatedText = parseMistralResponse(responseText);
            } else {
                translatedText = parseGeminiResponse(responseText);
            }
        } catch (Exception e) {
            LOGGER.warn("Error parsing response: " + responseText);
            handleHttpError(resp.statusCode());
            return;
        }

        resetHttpErrorFlags();

        if (translatedText == null || translatedText.isBlank()) return;

        translatedText = cleanText(translatedText);

        if (!isScreenShot) {
            translationCache.put(originalText, translatedText);
            LOGGER.debug("Translated: " + fixedText + " -> " + translatedText);
        } else {
            showScreenShotResult(translatedText);
        }

        LOGGER.debug("status: " + resp.statusCode());
    }

    private static boolean isOllamaResponse(String responseText) {
        return responseText.contains("\"response\"");
    }

    private static boolean isMistralResponse(String responseText) {
        try {
            JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();
            if (root.has("choices")) {
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    if (firstChoice.has("message")) {
                        JsonObject message = firstChoice.getAsJsonObject("message");
                        return message.has("content");
                    }
                }
            }
        } catch (Exception e) {
            // JSON 解析失敗，肯定不是有效 Ollama 回應
            return false;
        }
        return false;
    }

    private static String parseGeminiResponse(String responseText) {

        JsonObject response = JsonParser
                .parseString(responseText)
                .getAsJsonObject();

        return response.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    public static String parseOllamaResponse(String responseText) {
        JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();

        if (root.has("response")) {
            return root.get("response").getAsString();
        }

        return null;
    }

    public static String parseMistralResponse(String responseText) {
        JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();

        // 先抓 choices array
        if (root.has("choices")) {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message.has("content")) {
                        return message.get("content").getAsString();
                    }
                }
            }
        }

        return null;
    }

    private static void handleHttpError(int statusCode) {

        switch (statusCode) {

            case 403 -> showMessage(
                    "Translation failed! Check Your API Key in config!",
                    "無法翻譯! 請檢查你的 config 資料夾的 API KEY",
                    ChatFormatting.YELLOW,
                    () -> hasShowAPIKEYError,
                    () -> hasShowAPIKEYError = true
            );

            case 429 -> showMessage(
                    "Translation failed! You request too frequently (RPM exceeded)",
                    "無法翻譯! 請求過快導致超過 RPM 限制",
                    ChatFormatting.YELLOW,
                    () -> hasShowRequestTooFrequentError,
                    () -> hasShowRequestTooFrequentError = true
            );

            default -> showMessage(
                    "Translation failed! HTTP Status Code: " + statusCode,
                    "翻譯失敗! HTTP 回傳碼: " + statusCode,
                    ChatFormatting.RED,
                    () -> hasShowOtherError,
                    () -> hasShowOtherError = true
            );
        }
    }

    private static void handleConnectionError(Throwable throwable) {

        LOGGER.warn("Translation request failed: " + throwable.getMessage());

        showMessage(
                "Translate failed! Check Your Internet Connection",
                "無法翻譯! 請檢查網路連線",
                ChatFormatting.YELLOW,
                () -> hasShowConnectionError,
                () -> hasShowConnectionError = true
        );
    }

    private static void showMessage(String en,
                                    String zh,
                                    ChatFormatting color,
                                    BooleanSupplier flagCheck,
                                    Runnable flagSet) {

        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null && !flagCheck.getAsBoolean()) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal(en).withStyle(color));
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal(zh).withStyle(color));
                flagSet.run();
            }
        });
    }

    private static void showScreenShotResult(String translatedText) {

        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("螢幕翻譯結果:\n" + translatedText)
                                .withStyle(Translator.translatedStyle)
                );
                sendDataToScreen(translatedText);
            }
        });
    }

    private static String cleanText(String text) {
        return text.replace("\n", " ")
                .replaceAll("\\p{Cntrl}", "")
                .trim();
    }

    private static void resetHttpErrorFlags() {
        hasShowAPIKEYError = false;
        hasShowRequestTooFrequentError = false;
        hasShowOtherError = false;
        hasShowConnectionError = false;
    }

    private static void sendDataToScreen(String finalTranslatedText) {

        ScreenEventRender.setRenderText(finalTranslatedText);
    }
}
