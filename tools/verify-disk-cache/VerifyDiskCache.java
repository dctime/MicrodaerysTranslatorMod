// Regression check for net.github.dctime.libs.TranslationDiskCache (#4: local disk cache).
// Calls the REAL production class directly against a real temp directory on disk -- no
// Minecraft/NeoForge dependency needed, same as JsonUtil/RetryPolicy/TargetLanguage.
//
// Not covered here: Translator.loadCacheFromDisk()/flushCacheToDiskIfDirty() (the glue that
// resolves FMLPaths.CONFIGDIR and wires this into the CacheKey record) can't be exercised
// headless, for the same reason as every other Translator-touching check in this tools/
// directory -- Translator's static field initializer touches Minecraft classes.
//
// The concurrent-save case below is deliberately narrow: it only proves save() itself can't
// corrupt the file or throw when two calls overlap (unique-per-call tmp filenames -- see
// save()'s javadoc). It does NOT prove, and cannot prove from here, any ordering guarantee
// between two racing calls -- there isn't one at this layer. Translator gets ordering (its GUI's
// "clear cache" path landing after, not before, a periodic flush that started first) from its own
// single-thread cache-write executor serializing calls INTO save(), which this test doesn't
// exercise (Translator can't be loaded headless).
//
// Run:
//   GSON=$(find ~/.gradle -name 'gson-2.10.1.jar' | head -1)
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$GSON:$MAIN_CLASSES" -d tools/verify-disk-cache tools/verify-disk-cache/VerifyDiskCache.java
//   java -cp "tools/verify-disk-cache:$GSON:$MAIN_CLASSES" VerifyDiskCache

import net.github.dctime.libs.TranslationDiskCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VerifyDiskCache {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("verify-disk-cache");

        // --- missing file: empty cache, no exception ---
        Path missing = tempDir.resolve("does-not-exist").resolve("translation_cache.json");
        Map<String, Map<String, String>> fromMissing = TranslationDiskCache.load(missing);
        assertTrue("loading a missing file returns an empty (not null) map", fromMissing != null && fromMissing.isEmpty());

        // --- round trip through save()/load(), including the parent directory not existing yet ---
        Path file = tempDir.resolve("nested").resolve("translation_cache.json");
        Map<String, Map<String, String>> original = new HashMap<>();
        Map<String, String> zhTw = new HashMap<>();
        zhTw.put("Iron Ingot", "鐵錠");
        zhTw.put("He said \"hi\"\nline2", "他說 \"嗨\"\n第二行"); // quotes/newlines must survive Gson round-trip
        original.put("zh-tw", zhTw);
        Map<String, String> ja = new HashMap<>();
        ja.put("Iron Ingot", "鉄インゴット");
        original.put("ja", ja);

        TranslationDiskCache.save(file, original);
        assertTrue("save() creates the parent directory if missing", Files.isDirectory(file.getParent()));
        assertTrue("save() leaves the real file in place", Files.isRegularFile(file));
        assertTrue("save() does not leave a leftover .tmp file behind",
                !Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));

        Map<String, Map<String, String>> reloaded = TranslationDiskCache.load(file);
        assertTrue("round-tripped data matches exactly, including quotes/newlines", reloaded.equals(original));

        // --- overwrite via the same atomic temp-file-then-rename path ---
        Map<String, Map<String, String>> updated = new HashMap<>();
        Map<String, String> en = new HashMap<>();
        en.put("Iron Ingot", "Iron Ingot"); // pretend target language changed to en
        updated.put("en", en);
        TranslationDiskCache.save(file, updated);
        Map<String, Map<String, String>> reloadedAfterOverwrite = TranslationDiskCache.load(file);
        assertTrue("a second save() fully replaces the previous content (not merged)",
                reloadedAfterOverwrite.equals(updated));

        // --- corrupt file: falls back to empty cache instead of throwing ---
        Path corrupt = tempDir.resolve("corrupt.json");
        Files.writeString(corrupt, "{ this is not valid json ][");
        Map<String, Map<String, String>> fromCorrupt = TranslationDiskCache.load(corrupt);
        assertTrue("a corrupt JSON file falls back to an empty cache instead of throwing",
                fromCorrupt != null && fromCorrupt.isEmpty());

        // --- two overlapping save() calls to the SAME path must not corrupt each other or throw,
        // even though which one's content "wins" is unspecified (see save()'s javadoc + the file
        // header comment above) ---
        Path concurrent = tempDir.resolve("concurrent.json");
        Map<String, Map<String, String>> contentA = Map.of("zh-tw", Map.of("A", "A"));
        Map<String, Map<String, String>> contentB = Map.of("zh-tw", Map.of("B", "B"));
        Thread writerA = new Thread(() -> {
            try {
                for (int i = 0; i < 50; i++) TranslationDiskCache.save(concurrent, contentA);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread writerB = new Thread(() -> {
            try {
                for (int i = 0; i < 50; i++) TranslationDiskCache.save(concurrent, contentB);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writerA.start();
        writerB.start();
        writerA.join();
        writerB.join();

        Map<String, Map<String, String>> afterRace = TranslationDiskCache.load(concurrent);
        assertTrue("after two threads race-writing different content 50x each, the file still "
                        + "parses as valid, uncorrupted JSON equal to ONE of the two contents (not a mix, not empty)",
                afterRace.equals(contentA) || afterRace.equals(contentB));
        long leftoverTmpFiles;
        try (var listing = Files.list(tempDir)) {
            leftoverTmpFiles = listing.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
        }
        assertTrue("no leftover .tmp files survive the race (each writer's move() either succeeded or the whole call threw)",
                leftoverTmpFiles == 0);

        System.out.println("ALL CHECKS PASSED");
    }
}
