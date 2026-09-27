package net.github.dctime.libs.routing;

import javax.annotation.Nullable;

/**
 * Pure decision logic for {@code TranslationRouter}'s "the eligible pool came up empty -- why?"
 * question (mailbox review round 027, point V2; extracted per round 028/032's follow-up). Takes
 * four already-computed scalars instead of {@code List<ProviderCandidate>}/{@code Config} directly
 * -- the same shape fix already applied to {@link FailureClassifier} (status code / {@code
 * Throwable} in, {@link ProviderFailureType} out) -- specifically so this can be exercised headless
 * (see tools/verify-empty-pool-failure-classifier), which the {@code List<ProviderCandidate>}-typed
 * version could not be: building a real {@code ProviderCandidate} list goes through {@code
 * ProviderPool}, which needs the live NeoForge classpath (same disclosed limitation {@code
 * ProviderConfigResolver.resolve()} already has elsewhere). The caller ({@code TranslationRouter})
 * still computes those four values from the real pool -- this class only owns what they MEAN.
 * <p>
 * Three genuinely different reasons the eligible pool can be empty -- conflating them into a bare
 * {@code null} would leave {@code Translator} to guess what a null {@code finalFailure} means,
 * which is exactly the question this class exists to answer instead:
 * <ol>
 *     <li>A screenshot with zero vision-capable candidates in the whole raw pool, regardless of
 *         enabled/credentials/cooldown -> {@link ProviderFailureType#UNSUPPORTED_CAPABILITY}
 *         ({@code Translator} maps this to the existing vision_unsupported message).</li>
 *     <li>Nothing in the raw pool is even structurally capable of serving ANY job right now -- not
 *         enabled, or enabled with no saved credentials, in a non-SINGLE mode (SINGLE always
 *         considers exactly one provider and never excludes it for lacking credentials, see {@code
 *         TranslationRouter.hardFilter}) -> {@link ProviderFailureType#NO_ELIGIBLE_PROVIDER}, a
 *         configuration problem that will not resolve itself -- the player must be told (e.g. they
 *         disabled every provider in Manage Providers).</li>
 *     <li>Otherwise: a real, structurally-eligible pool exists but every candidate is currently
 *         excluded by something transient (cooldown, budget exhaustion, already attempted this job)
 *         -> whatever the job's last real failure already was, which stays {@code null} if every
 *         exclusion so far was a budget skip rather than a real network attempt -- matches the
 *         pre-Router "dropped, not queued, retried next frame" semantics: say nothing, this isn't a
 *         problem the player can act on.</li>
 * </ol>
 */
public final class EmptyPoolFailureClassifier {

    private EmptyPoolFailureClassifier() {
    }

    /**
     * @param visionRequirement                     the job's own requirement.
     * @param anyRawCandidateSupportsVision          true if at least one candidate in the RAW pool
     *                                                (before any filtering) can accept an image.
     * @param mode                                   current routing mode -- SINGLE never triggers
     *                                                {@link ProviderFailureType#NO_ELIGIBLE_PROVIDER}
     *                                                (see class javadoc, point 2).
     * @param anyRawCandidateEnabledWithCredentials  true if at least one candidate in the RAW pool
     *                                                is both enabled and has real credentials.
     * @param lastFailureType                        the job's most recent classified failure, or
     *                                                {@code null} if none happened yet.
     */
    @Nullable
    public static ProviderFailureType classify(VisionRequirement visionRequirement,
                                                boolean anyRawCandidateSupportsVision,
                                                ProviderMode mode,
                                                boolean anyRawCandidateEnabledWithCredentials,
                                                @Nullable ProviderFailureType lastFailureType) {
        if (visionRequirement == VisionRequirement.REQUIRED && !anyRawCandidateSupportsVision) {
            return ProviderFailureType.UNSUPPORTED_CAPABILITY;
        }
        if (mode != ProviderMode.SINGLE && !anyRawCandidateEnabledWithCredentials) {
            return ProviderFailureType.NO_ELIGIBLE_PROVIDER;
        }
        return lastFailureType;
    }
}
