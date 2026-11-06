package net.github.dctime.libs;

import javax.annotation.Nullable;

/**
 * Pure JSON request-body building for the translation endpoints. No Minecraft/NeoForge
 * dependency on purpose, so it can be unit-tested (see tools/verify-json-escaping) and reused
 * without the full game runtime.
 */
public class JsonUtil {

    public static String escapeJson(String s) {
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
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String getGeminiJsonBody(String image, String prompt) {
        String safePrompt = escapeJson(prompt);
        if (image == null) {
            return ("{\n" +
                    "  \"contents\": [\n" +
                    "    { \"parts\": [\n" +
                    "     { \"text\": \"\n") + safePrompt + ("\"}\n" +
                        "]}\n" +
                        "  ]\n" +
                        "}\n");
        } else {
            return ("{\n" +
                    "  \"contents\": [\n" +
                    "    { \"parts\": [\n" +
                    "     { \"text\": \"\n") + safePrompt + "\"},{ \"inline_data\": {\"mime_type\": \"image/png\",\"data\":\"" + image + "\"}}]}]}";
        }
    }

    public static String buildOllamaJson(String prompt,
                                          @Nullable String imageBase64,
                                          String model) {

        String safePrompt = escapeJson(prompt);

        if (imageBase64 == null) {
            return String.format(("{\n" +
                    "  \"model\": \"%s\",\n" +
                    "  \"prompt\": \"%s\",\n" +
                    "  \"stream\": false\n" +
                    "}\n"), model, safePrompt);
        } else {

            // Ollama 圖片要用 images 陣列
            return String.format(("{\n" +
                    "  \"model\": \"%s\",\n" +
                    "  \"prompt\": \"%s\",\n" +
                    "  \"images\": [\"%s\"],\n" +
                    "  \"stream\": false\n" +
                    "}\n"), model, safePrompt, imageBase64);
        }
    }

    /**
     * Shared OpenAI-compatible {@code /chat/completions} request body -- used by every provider
     * whose API mirrors OpenAI's shape (NVIDIA NIM, Groq, OpenRouter, Mistral, DeepSeek, Cerebras,
     * OpenAI itself, and Custom Provider). Was {@code buildMistralJson}; Mistral's shape was
     * already this exact format, so this is a rename/generalization, not new duplication -- avoids
     * a near-identical {@code buildGroqJson}/{@code buildDeepSeekJson}/... per provider.
     */
    public static String buildOpenAiCompatibleJson(String prompt,
                                                     @Nullable String imageBase64,
                                                     String model) {

        String safePrompt = escapeJson(prompt);
        String safeModel = escapeJson(model);

        if (imageBase64 == null) {
            return String.format(("{\n" +
                    "  \"model\": \"%s\",\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"role\": \"user\",\n" +
                    "      \"content\": \"%s\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n"), safeModel, safePrompt);
        } else {
            return String.format(("{\n" +
                    "  \"model\": \"%s\",\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"role\": \"user\",\n" +
                    "      \"content\": [\n" +
                    "        {\n" +
                    "          \"type\": \"text\",\n" +
                    "          \"text\": \"%s\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "          \"type\": \"image_url\",\n" +
                    "          \"image_url\": \"data:image/png;base64,%s\"\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n"), safeModel, safePrompt, imageBase64);
        }
    }

    /**
     * Anthropic Messages API request body. Unlike the other three shapes, Anthropic REQUIRES
     * {@code max_tokens} on every request -- there's no server-side default to omit it and fall
     * back to. {@code maxTokens} is a fixed cap chosen by the caller (this mod's tooltips/prompts
     * are short, so a modest cap is plenty and keeps a runaway response bounded).
     */
    public static String buildAnthropicJson(String prompt,
                                             @Nullable String imageBase64,
                                             String model,
                                             int maxTokens) {

        String safePrompt = escapeJson(prompt);
        String safeModel = escapeJson(model);

        if (imageBase64 == null) {
            return String.format(("{\n" +
                    "  \"model\": \"%s\",\n" +
                    "  \"max_tokens\": %d,\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"role\": \"user\",\n" +
                    "      \"content\": \"%s\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n"), safeModel, maxTokens, safePrompt);
        } else {
            return String.format(("{\n" +
                    "  \"model\": \"%s\",\n" +
                    "  \"max_tokens\": %d,\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"role\": \"user\",\n" +
                    "      \"content\": [\n" +
                    "        {\n" +
                    "          \"type\": \"text\",\n" +
                    "          \"text\": \"%s\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "          \"type\": \"image\",\n" +
                    "          \"source\": {\n" +
                    "            \"type\": \"base64\",\n" +
                    "            \"media_type\": \"image/png\",\n" +
                    "            \"data\": \"%s\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n"), safeModel, maxTokens, safePrompt, imageBase64);
        }
    }
}
