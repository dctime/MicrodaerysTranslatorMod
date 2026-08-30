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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

import static net.github.dctime.libs.ScreenShotter.getItemStackImage;

public class Translator {
    // language is part of the key so switching Config.TARGET_LANGUAGE doesn't serve a cached
    // translation from a previous target language. A record has built-in equals/hashCode, so
    // there's no string-concatenation-with-a-delimiter to accidentally collide with real content.
    private record CacheKey(String lang, String text) {}

    private static CacheKey keyFor(String text) {
        return new CacheKey(Config.TARGET_LANGUAGE.get(), text);
    }

    private static ConcurrentHashMap<CacheKey, String> translationCache = new ConcurrentHashMap<>();

    // per-text in-flight tracking replaces the old single global "translating" lock, which
    // dropped every request but the first when hovering across several items in one frame.
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    // 429 (RPM) backoff bookkeeping, keyed by the same text as IN_FLIGHT/translationCache.
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RETRY_ATTEMPTS = new ConcurrentHashMap<>();
    // caps how many translation requests can be in flight at once across ALL texts, so sweeping
    // the mouse over a long row of items doesn't fire off unbounded concurrent requests.
    private static final Semaphore CONCURRENCY_LIMIT = new Semaphore(4);
    // screenshot translation is a single, unrelated flow (fixed ":" text) with its own busy flag.
    public static volatile boolean screenshotTranslating = false;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static boolean isTranslating() {
        return screenshotTranslating || !IN_FLIGHT.isEmpty();
    }
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
            translationCache.remove(keyFor(text));
//            System.out.println("REMOVE TRANSLATION: " + text);
            return false;
        }

//        System.out.println("FINDING TRNASLATION IN CACHE: " + text + " deleting: " + deletingTranslationKeyHold);
        return translationCache.containsKey(keyFor(text));
    }

    public static String getTranslationFromCache(String text) {
        return translationCache.get(keyFor(text));
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
        String prompt = resolvePrompt(isScreenShot) + "\n" + textInEnglish;
        if (isScreenShot) {
            prompt = resolvePrompt(true);
        }

        String jsonBody = JsonUtil.getGeminiJsonBody(image, prompt);

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
            translationCache.put(keyFor(textInEnglish), "");
            return null;
        }

        return req;
    }

    public static HttpRequest setupRequestOllama(String textInEnglish,
                                                 @Nullable String imageBase64,
                                                 boolean isScreenShot) {

        if (textInEnglish == null || textInEnglish.isBlank()) {
            return null;
        }

        String prompt = resolvePrompt(isScreenShot) + "\n" + textInEnglish;

        String model = Config.MODEL_NAME.get(); // 例如 "phi3"

        String url = "http://127.0.0.1:11434/api/generate";

        String jsonBody = JsonUtil.buildOllamaJson(prompt, imageBase64, model);

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

        String prompt = resolvePrompt(isScreenShot) + "\n" + textInEnglish;

        String model = Config.MODEL_NAME.get();

        String url = "https://api.mistral.ai/v1/chat/completions";

        String jsonBody = JsonUtil.buildMistralJson(prompt, imageBase64, model);

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Config.TIMEOUT_DURATION_CONFIG.get()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + Config.API_KEY.get())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private static String resolvePrompt(boolean isScreenShot) {
        String template = isScreenShot ? Config.PROMPT_SCREENSHOT.get() : Config.PROMPT.get();
        // PROMPT/PROMPT_SCREENSHOT are freely player-editable config strings, so this must be a
        // literal substitution, not String.format()/.formatted() semantics: a lone '%' typed into
        // a custom prompt (e.g. "不要翻超過 90% 的內容") would make .formatted() throw
        // IllegalFormatException, which nothing upstream catches -- a config edit that once was
        // harmless plain text would crash every tooltip render. .replace() can never throw.
        return template.replace("%s", TargetLanguage.displayName(Config.TARGET_LANGUAGE.get()));
    }

    public static void requestTranslateToTraditionalChinese(String textInEnglish) throws IOException, InterruptedException {
        requestTranslateToTraditionalChinese(textInEnglish, null, false);
    }

    public static void requestTranslateItemStackToTraditionalChinese(String textInEnglish, ItemStack stack) throws IOException, InterruptedException {
        if (stack != null && !IN_FLIGHT.contains(textInEnglish) && Config.ENABLE_ICON_CONFIG.get()) {
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

        String fixedText = textInEnglish;

        if (TargetLanguage.isAlreadyInTargetLanguage(Config.TARGET_LANGUAGE.get(), fixedText)) {
            translationCache.put(keyFor(fixedText), "");
            LOGGER.debug("Text already in the target language, skipping translation: " + fixedText);
            return;
        }

        if (isScreenShot) {
            if (screenshotTranslating) return;
        } else {
            if (IN_FLIGHT.contains(fixedText)) return;
            Long retryAfter = RETRY_AFTER.get(fixedText);
            if (retryAfter != null) {
                if (System.currentTimeMillis() < retryAfter) return; // still backing off after a 429
                RETRY_AFTER.remove(fixedText);
            }
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

        if (!CONCURRENCY_LIMIT.tryAcquire()) return; // too many requests already in flight; a later render tick retries

        // acquire/release must stay paired 1:1 with this exact ordering; a mismatch here isn't
        // caught by any automated test (see the disclosed limitation at the top of
        // tools/verify-concurrency/VerifyConcurrency.java) -- review this finally block by eye
        // before changing it.
        if (isScreenShot) screenshotTranslating = true; else IN_FLIGHT.add(fixedText);

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, throwable) -> {
                    try {
                        if (throwable != null) {
                            handleConnectionError(throwable);
                            return;
                        }

                        handleHttpResponse(resp, fixedText, isScreenShot);

                    } finally {
                        CONCURRENCY_LIMIT.release();
                        if (isScreenShot) screenshotTranslating = false; else IN_FLIGHT.remove(fixedText);
                    }
                });
    }

    private static void handleHttpResponse(HttpResponse<String> resp,
                                           String text,
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
            handleHttpError(resp.statusCode(), text, isScreenShot);
            return;
        }

        resetHttpErrorFlags();
        if (!isScreenShot) RETRY_ATTEMPTS.remove(text);

        if (translatedText == null || translatedText.isBlank()) return;

        translatedText = cleanText(translatedText);

        if (!isScreenShot) {
            translationCache.put(keyFor(text), translatedText);
            LOGGER.debug("Translated: " + text + " -> " + translatedText);
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

    private static void handleHttpError(int statusCode, String text, boolean isScreenShot) {

        switch (statusCode) {

            case 403 -> showMessage(
                    "Translation failed! Check Your API Key in config!",
                    "無法翻譯! 請檢查你的 config 資料夾的 API KEY",
                    ChatFormatting.YELLOW,
                    () -> hasShowAPIKEYError,
                    () -> hasShowAPIKEYError = true
            );

            case 429 -> {
                if (!isScreenShot) scheduleRetryBackoff(text);
                showMessage(
                        "Translation failed! You request too frequently (RPM exceeded)",
                        "無法翻譯! 請求過快導致超過 RPM 限制",
                        ChatFormatting.YELLOW,
                        () -> hasShowRequestTooFrequentError,
                        () -> hasShowRequestTooFrequentError = true
                );
            }

            default -> showMessage(
                    "Translation failed! HTTP Status Code: " + statusCode,
                    "翻譯失敗! HTTP 回傳碼: " + statusCode,
                    ChatFormatting.RED,
                    () -> hasShowOtherError,
                    () -> hasShowOtherError = true
            );
        }
    }

    // simple exponential backoff (4s, 8s, 16s, capped at 30s) before this exact text is allowed
    // to be retried again, so a burst of 429s doesn't just get retried every render frame.
    private static void scheduleRetryBackoff(String text) {
        int attempt = RETRY_ATTEMPTS.merge(text, 1, Integer::sum);
        RETRY_AFTER.put(text, System.currentTimeMillis() + RetryPolicy.backoffDelayMs(attempt));
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
