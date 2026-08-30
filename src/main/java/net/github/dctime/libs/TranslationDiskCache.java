package net.github.dctime.libs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure JSON persistence for the translation cache (language -> text -> translated text), shaped
 * to match Translator's CacheKey(lang, text). No Minecraft/NeoForge dependency on purpose (see
 * JsonUtil/RetryPolicy/TargetLanguage) -- the caller resolves the actual file path.
 */
public class TranslationDiskCache {
    private static final Gson GSON = new Gson();
    private static final Type NESTED_MAP_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();

    /** Never throws: a missing or corrupt file just yields an empty cache. */
    public static Map<String, Map<String, String>> load(Path file) {
        if (!Files.isRegularFile(file)) return new HashMap<>();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Map<String, String>> data = GSON.fromJson(json, NESTED_MAP_TYPE);
            return data != null ? data : new HashMap<>();
        } catch (IOException | JsonSyntaxException e) {
            return new HashMap<>();
        }
    }

    /**
     * Writes to a sibling ".tmp" file then atomically renames it over the target, so a crash
     * (or another flush racing in) mid-write never leaves a half-written, corrupt JSON file in
     * the real cache path.
     */
    public static void save(Path file, Map<String, Map<String, String>> data) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(data, NESTED_MAP_TYPE), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
