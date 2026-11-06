// Regression check for TargetLanguage.isInheritedEnglishTarget (R1 / #5): the join-time warning
// must fire exactly when follow_game_language=true resolves the target to en_us, and never for a
// hand-picked en_us or any non-English target.
//
// Run:
//   ./gradlew build
//   javac -cp build/classes/java/main -d tools/verify-english-target-warning tools/verify-english-target-warning/VerifyEnglishTargetWarning.java
//   java  -cp "tools/verify-english-target-warning:build/classes/java/main" VerifyEnglishTargetWarning
//
// Does NOT verify: that PlayerJoinWorldEvent actually calls Translator.warnIfTargetFollowsEnglishGameLanguage,
// that resolveTargetLanguage() feeds it the real game language, that showMessage reaches chat, or
// that the en_us/zh_tw lang values resolve (verify-lang-placeholders only checks key parity) --
// all of that needs a running client.
import net.github.dctime.libs.TargetLanguage;

public class VerifyEnglishTargetWarning {
    private static int failures = 0;

    private static void check(String name, boolean actual, boolean expected) {
        if (actual != expected) {
            failures++;
            System.out.println("FAIL: " + name + " expected " + expected + " got " + actual);
        } else {
            System.out.println("OK: " + name);
        }
    }

    public static void main(String[] args) {
        check("follow=true + en_us warns", TargetLanguage.isInheritedEnglishTarget(true, "en_us"), true);
        check("follow=true + EN_US (case) warns", TargetLanguage.isInheritedEnglishTarget(true, " EN_US "), true);
        check("follow=false + en_us (hand-picked) silent", TargetLanguage.isInheritedEnglishTarget(false, "en_us"), false);
        check("follow=true + zh_tw silent", TargetLanguage.isInheritedEnglishTarget(true, "zh_tw"), false);
        check("follow=true + en_gb silent (not the ASCII skip pattern)", TargetLanguage.isInheritedEnglishTarget(true, "en_gb"), false);
        check("follow=true + null silent", TargetLanguage.isInheritedEnglishTarget(true, null), false);
        if (failures > 0) {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
        System.out.println("ALL CHECKS PASSED");
    }
}
