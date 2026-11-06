// Regression check for net.github.dctime.libs.WelcomeMessageTemplates (#19: the join-message in
// PlayerJoinWorldEvent was hardcoded to Traditional Chinese only; the user asked for it to follow
// whatever language Minecraft's own UI is currently showing, same as any other UI chrome).
//
// WelcomeMessageTemplates is pure (no Minecraft dependency), so this calls the REAL production
// class directly -- no copy, no headless-limitation caveat needed here.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-welcome-message tools/verify-welcome-message/VerifyWelcomeMessage.java
//   java -cp "tools/verify-welcome-message:$MAIN_CLASSES" VerifyWelcomeMessage

import net.github.dctime.libs.WelcomeMessageTemplates;

import java.util.List;
import java.util.Set;

public class VerifyWelcomeMessage {

    private static void assertTrue(String label, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + label);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        Set<String> supported = Set.of("zh_tw", "zh_cn", "ja_jp", "en_us", "es_es", "fr_fr");

        for (String lang : supported) {
            List<String> lines = WelcomeMessageTemplates.linesFor(lang);
            assertTrue(lang + ": has lines", !lines.isEmpty());
            assertTrue(lang + ": no line is blank", lines.stream().noneMatch(String::isBlank));
            String clickHere = WelcomeMessageTemplates.clickHereFor(lang);
            assertTrue(lang + ": click-here text is non-blank", clickHere != null && !clickHere.isBlank());
        }

        assertTrue("zh_tw lines differ from zh_cn (not the same table reused by mistake)",
                !WelcomeMessageTemplates.linesFor("zh_tw").equals(WelcomeMessageTemplates.linesFor("zh_cn")));

        assertTrue("unknown language code falls back to en_us lines, not a crash/empty list",
                WelcomeMessageTemplates.linesFor("xx_xx").equals(WelcomeMessageTemplates.linesFor("en_us")));
        assertTrue("unknown language code falls back to en_us click-here text",
                WelcomeMessageTemplates.clickHereFor("xx_xx").equals(WelcomeMessageTemplates.clickHereFor("en_us")));

        assertTrue("null language code falls back instead of throwing",
                WelcomeMessageTemplates.linesFor(null).equals(WelcomeMessageTemplates.linesFor("en_us")));

        System.out.println("ALL CHECKS PASSED");
    }
}
