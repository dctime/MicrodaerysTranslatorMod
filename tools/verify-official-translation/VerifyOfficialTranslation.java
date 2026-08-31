// Regression check for net.github.dctime.libs.OfficialTranslationLookup (#6: skip AI when an
// official translation already exists; #10: compare against the game's ACTUAL current display
// language instead of always assuming English).
//
// OfficialTranslationLookup.decide() is a pure decision function pulled out specifically so it
// can be called directly here without a running game -- unlike Translator, this class has no
// eager static field initializer touching Minecraft classes, so merely loading the class and
// calling decide() (never calling forLanguage(), which touches Minecraft.getInstance()) does not
// trigger NoClassDefFoundError. lookup() and invalidateCache() (the ClientLanguage/ResourceManager
// wiring, including the resource-reload cache invalidation) are NOT exercised here -- that needs
// a real running game with resource packs loaded, same class of limitation as everywhere else in
// tools/.
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
        // --- the original scenario: English-UI client, translating into Chinese ---
        assertTrue("default name + official target translation exists -> use it",
                "鐵錠".equals(OfficialTranslationLookup.decide(true, "Iron Ingot", "Iron Ingot", "鐵錠")));

        // --- #10: a Chinese-UI client translating a vanilla item INTO English (the case that was
        // broken before -- the safety check used to always compare against en_us's value "Wool",
        // which never matches a Chinese client's actually-rendered "羊毛", so this always fell
        // through to the AI even though it's a pure vanilla-to-vanilla lookup) ---
        assertTrue("Chinese-UI client + English target: currently-rendered 羊毛 matches the CURRENT " +
                        "(zh_tw) language's value, not en_us's -> official English translation used directly",
                "Wool".equals(OfficialTranslationLookup.decide(true, "羊毛", "羊毛", "Wool")));

        // --- current display language has no entry for this key at all (e.g. a key that doesn't exist) ---
        assertTrue("current language has no entry for the key -> null (fall back to AI)",
                OfficialTranslationLookup.decide(false, "", "Iron Ingot", "鐵錠") == null);

        // --- the safety check: rendered text does not match the official value in whatever
        // language the game is currently displaying, meaning this is a custom-renamed item
        // (anvil, /data component, ...) sharing the same registry key ---
        assertTrue("rendered text differs from the current language's official value (custom rename) -> null, don't substitute",
                OfficialTranslationLookup.decide(true, "Iron Ingot", "Excalibur", "鐵錠") == null);

        // --- target language has no official translation for this key (e.g. a modded item with
        // only an en_us lang file, no zh_tw one) -- fall back to AI ---
        assertTrue("target language has no official translation for this key -> null (fall back to AI)",
                OfficialTranslationLookup.decide(true, "Iron Ingot", "Iron Ingot", null) == null);

        // --- exact match is required, not just "close enough" ---
        assertTrue("even a trailing-space difference from the current language's official value counts as a mismatch",
                OfficialTranslationLookup.decide(true, "Iron Ingot", "Iron Ingot ", "鐵錠") == null);

        System.out.println("ALL CHECKS PASSED");
    }
}
