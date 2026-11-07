package net.github.dctime.libs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
     * Writes to a sibling, uniquely-named ".tmp" file then atomically renames it over the target,
     * so a crash mid-write never leaves a half-written, corrupt JSON file in the real cache path.
     * <p>
     * The tmp filename includes a random UUID specifically so two overlapping calls (e.g. a
     * periodic flush and an on-demand one, both triggered from Translator around the same moment)
     * can never write into the SAME tmp file and corrupt each other into a mixed/invalid JSON
     * blob, and can't hit each other with a "the other one already moved this exact tmp path away"
     * {@code NoSuchFileException} either. What this does NOT provide: an ordering guarantee.
     * {@code Files.move(..., ATOMIC_MOVE)} makes each individual move atomic, but if two calls
     * race, whichever one's {@code move} lands LAST wins on disk, regardless of which call started
     * first or which one has "newer" data -- a caller that needs deterministic last-call-wins
     * semantics (see Translator's single-thread cache-write executor) must serialize its own calls
     * into this method itself; this method only promises each individual call is atomic and
     * mutually non-corrupting.
     */
    public static void save(Path file, Map<String, Map<String, String>> data) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(tmp, GSON.toJson(data, NESTED_MAP_TYPE), StandardCharsets.UTF_8);
        try {
            // Windows (MoveFileEx) refuses to replace a file another move is replacing at that
            // same instant -- two overlapping save() calls threw AccessDeniedException and left
            // their .tmp behind (verify-disk-cache failed 4 of 5 runs on Windows). Serializing the
            // replace within this process removes that; the short retry covers the same error
            // caused by something outside it (antivirus/indexer briefly holding the file).
            synchronized (MOVE_LOCK) {
                moveReplacing(tmp, file);
            }
        } finally {
            Files.deleteIfExists(tmp); // no-op after a successful move; never leave a stray .tmp on failure
        }
    }

    private static final Object MOVE_LOCK = new Object();
    private static final int MOVE_ATTEMPTS = 5;

    private static void moveReplacing(Path tmp, Path file) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= MOVE_ATTEMPTS) throw e;
                try {
                    Thread.sleep(20L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
