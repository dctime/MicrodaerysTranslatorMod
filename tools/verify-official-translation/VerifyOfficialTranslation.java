// Regression check for net.github.dctime.libs.OfficialTranslationLookup (#6: skip AI when an
// official translation already exists).
//
// OfficialTranslationLookup.decide() is a pure decision function pulled out specifically so it
// can be called directly here without a running game -- unlike Translator, this class has no
// eager static field initializer touching Minecraft classes, so merely loading the class and
// calling decide() (never calling enUs()/forTargetLanguage(), which do touch Minecraft.getInstance())
// does not trigger NoClassDefFoundError. lookup() and invalidateCache() (the ClientLanguage/
// ResourceManager wiring, including the resource-reload cache invalidation) are NOT exercised
// here -- that needs a real running game with resource packs loaded, same class of limitation
// as everywhere else in tools/.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-official-translation tools/verify-official-translation/VerifyOfficialTranslation.java
//   java -cp "tools/verify-official-translation:$MAIN_CLASSES" VerifyOfficialTranslation

import net.github.dctime.libs.OfficialTranslationLookup;

public class VerifyOfficialTranslation {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- the happy path: default name, official translation exists ---
        assertTrue("default name + official target translation exists -> use it",
                "鐵錠".equals(OfficialTranslationLookup.decide(true, "Iron Ingot", "Iron Ingot", "鐵錠")));

        // --- en_us has no entry for this key at all (e.g. a key that doesn't exist) ---
        assertTrue("en_us has no entry for the key -> null (fall back to AI)",
                OfficialTranslationLookup.decide(false, "", "Iron Ingot", "鐵錠") == null);

        // --- the safety check: rendered text does not match the official en_us name, meaning
        // this is a custom-renamed item (anvil, /data component) sharing the same registry key ---
        assertTrue("rendered text differs from official en_us value (custom rename) -> null, don't substitute",
                OfficialTranslationLookup.decide(true, "Iron Ingot", "Excalibur", "鐵錠") == null);

        // --- target language has no official translation for this key (e.g. a modded item with
        // only an en_us lang file, no zh_tw one) -- fall back to AI ---
        assertTrue("target language has no official translation for this key -> null (fall back to AI)",
                OfficialTranslationLookup.decide(true, "Iron Ingot", "Iron Ingot", null) == null);

        // --- exact match is required, not just "close enough" ---
        assertTrue("even a trailing-space difference from the official en_us value counts as a mismatch",
                OfficialTranslationLookup.decide(true, "Iron Ingot", "Iron Ingot ", "鐵錠") == null);

        System.out.println("ALL CHECKS PASSED");
    }
}
