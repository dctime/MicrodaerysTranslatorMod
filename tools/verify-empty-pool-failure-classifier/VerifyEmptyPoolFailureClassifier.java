// Regression check for net.github.dctime.libs.routing.EmptyPoolFailureClassifier -- the pure
// decision logic behind "the eligible pool came up empty, what does that MEAN" (mailbox review
// round 027 point V2, extracted into a pure/testable shape per round 028/032's follow-up, matching
// the same fix already applied to FailureClassifier). No Minecraft dependency, calls the REAL
// production class directly.
//
// What this does NOT verify: that TranslationRouter actually computes the four scalar inputs
// (anyRawCandidateSupportsVision, anyRawCandidateEnabledWithCredentials, etc.) correctly from a
// real ProviderCandidate pool -- that still needs the live NeoForge classpath (ProviderPool ->
// ProviderConfigResolver.resolve(), same disclosed limitation tools/verify-provider-adapters
// already states). This file only checks the classification FORMULA itself in isolation, exactly
// like tools/verify-provider-scorer only checks ProviderScorer's formula, not AutomaticRoutingStrategy
// feeding it real numbers.
//
// Run:
//   MAIN_CLASSES=build/classes/java/main
//   javac -cp "$MAIN_CLASSES" -d tools/verify-empty-pool-failure-classifier tools/verify-empty-pool-failure-classifier/VerifyEmptyPoolFailureClassifier.java
//   java -cp "tools/verify-empty-pool-failure-classifier:$MAIN_CLASSES" VerifyEmptyPoolFailureClassifier

import net.github.dctime.libs.routing.EmptyPoolFailureClassifier;
import net.github.dctime.libs.routing.ProviderFailureType;
import net.github.dctime.libs.routing.ProviderMode;
import net.github.dctime.libs.routing.VisionRequirement;

public class VerifyEmptyPoolFailureClassifier {

    private static void assertEquals(String label, ProviderFailureType expected, ProviderFailureType actual) {
        if (expected != actual) throw new AssertionError("FAILED: " + label + " -- expected " + expected + " got " + actual);
        System.out.println("OK: " + label);
    }

    public static void main(String[] args) {
        // --- path 1: screenshot (VISION REQUIRED), nothing in the whole raw pool supports vision ---
        assertEquals("screenshot with zero vision-capable candidates anywhere -> UNSUPPORTED_CAPABILITY",
                ProviderFailureType.UNSUPPORTED_CAPABILITY,
                EmptyPoolFailureClassifier.classify(VisionRequirement.REQUIRED, false, ProviderMode.AUTOMATIC, true, null));

        assertEquals("REQUIRED vision check wins even in SINGLE mode (vision is checked before the mode check)",
                ProviderFailureType.UNSUPPORTED_CAPABILITY,
                EmptyPoolFailureClassifier.classify(VisionRequirement.REQUIRED, false, ProviderMode.SINGLE, true, null));

        assertEquals("REQUIRED vision check wins even with a real prior failure recorded (vision is checked first)",
                ProviderFailureType.UNSUPPORTED_CAPABILITY,
                EmptyPoolFailureClassifier.classify(VisionRequirement.REQUIRED, false, ProviderMode.AUTOMATIC, true,
                        ProviderFailureType.SERVER));

        // --- path 2: non-SINGLE mode, nothing in the raw pool is even enabled+credentialed ---
        assertEquals("AUTOMATIC mode, nothing enabled+credentialed anywhere -> NO_ELIGIBLE_PROVIDER",
                ProviderFailureType.NO_ELIGIBLE_PROVIDER,
                EmptyPoolFailureClassifier.classify(VisionRequirement.NONE, true, ProviderMode.AUTOMATIC, false, null));

        assertEquals("PRIORITY mode, nothing enabled+credentialed anywhere -> NO_ELIGIBLE_PROVIDER",
                ProviderFailureType.NO_ELIGIBLE_PROVIDER,
                EmptyPoolFailureClassifier.classify(VisionRequirement.NONE, true, ProviderMode.PRIORITY, false, null));

        assertEquals("ROUND_ROBIN mode, nothing enabled+credentialed anywhere -> NO_ELIGIBLE_PROVIDER",
                ProviderFailureType.NO_ELIGIBLE_PROVIDER,
                EmptyPoolFailureClassifier.classify(VisionRequirement.NONE, true, ProviderMode.ROUND_ROBIN, false, null));

        // --- SINGLE mode is explicitly exempt from the NO_ELIGIBLE_PROVIDER check (SINGLE always
        // attempts its one configured provider regardless of credentials -- see TranslationRouter's
        // hardFilter javadoc for why) ---
        assertEquals("SINGLE mode, nothing enabled+credentialed anywhere -> still falls through, not NO_ELIGIBLE_PROVIDER",
                null,
                EmptyPoolFailureClassifier.classify(VisionRequirement.NONE, true, ProviderMode.SINGLE, false, null));

        // --- path 3: neither special case applies -- falls through to whatever the job's last real
        // failure already was (possibly null, meaning "nothing actually happened, stay silent") ---
        assertEquals("normal pool, real prior failure recorded -> that failure is returned as-is",
                ProviderFailureType.RATE_LIMIT,
                EmptyPoolFailureClassifier.classify(VisionRequirement.NONE, true, ProviderMode.AUTOMATIC, true,
                        ProviderFailureType.RATE_LIMIT));

        assertEquals("normal pool, no prior failure recorded (every exclusion was a budget skip) -> null, stay silent",
                null,
                EmptyPoolFailureClassifier.classify(VisionRequirement.NONE, true, ProviderMode.AUTOMATIC, true, null));

        // --- OPTIONAL vision requirement never triggers the vision check (only REQUIRED does) ---
        assertEquals("OPTIONAL vision requirement with zero vision-capable candidates does NOT trigger "
                        + "UNSUPPORTED_CAPABILITY -- falls through to the eligibility check instead",
                ProviderFailureType.NO_ELIGIBLE_PROVIDER,
                EmptyPoolFailureClassifier.classify(VisionRequirement.OPTIONAL, false, ProviderMode.AUTOMATIC, false, null));

        System.out.println("ALL CHECKS PASSED");
    }
}
