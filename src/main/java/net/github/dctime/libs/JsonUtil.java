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
            return """
                {
                  "contents": [
                    { "parts": [
                     { "text": "
                """ + safePrompt + """
                "}
                ]}
                  ]
                }
                """;
        } else {
            return """
                    {
                      "contents": [
                        { "parts": [
                         { "text": "
                    """ + safePrompt + "\"},{ \"inline_data\": {\"mime_type\": \"image/png\",\"data\":\"" + image + "\"}}]}]}";
        }
    }

    public static String buildOllamaJson(String prompt,
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

    public static String buildMistralJson(String prompt,
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
}
