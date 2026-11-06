package net.github.dctime.libs.routing;

import net.github.dctime.Config;
import net.github.dctime.libs.BaseUrlUtil;
import net.github.dctime.libs.provider.ProviderSettings;
import net.github.dctime.libs.provider.TranslationProviderAdapter;

/**
 * One pool member at ranking time -- everything a {@link RoutingStrategy} or the {@link
 * TranslationRouter}'s hard filter needs about a single provider, bundled together so neither has
 * to re-look-up {@code Config}/{@code ProviderConfigResolver}/{@code ProviderAdapterRegistry}
 * itself. Built fresh per {@link TranslationRouter#translate} call by {@code ProviderPool} --
 * never cached across jobs, since {@code enabled}/{@code apiKey}/{@code priority}/{@code
 * maxRequestsPerMinute} can change between jobs (the player edited Manage Providers) while {@link
 * #runtimeState} itself is the one long-lived, cross-job piece (see {@link
 * ProviderRuntimeRegistry}).
 *
 * @param supportsVision  whether the CURRENT model (see {@link ProviderSettings#supportsVision()})
 *                        accepts image input -- the hard filter used for {@link
 *                        VisionRequirement#REQUIRED}; {@link VisionRequirement#OPTIONAL} ignores
 *                        this and just omits the image when building the request instead.
 * @param priority        1 = highest. Only a bias in AUTOMATIC (see {@link ProviderScorer}), a hard
 *                        sort key in PRIORITY.
 * @param enabled         the player's saved on/off toggle for this provider (ignored entirely in
 *                        SINGLE mode, which only ever considers {@code Config.ENDPOINT_CONFIG}'s
 *                        provider). {@code enabled=true} does NOT mean eligible -- see {@link
 *                        #hasCredentials()}; the router must never attempt a keyless enabled
 *                        provider.
 */
public record ProviderCandidate(Config.EndPoint endpoint, TranslationProviderAdapter adapter,
                                 ProviderSettings settings, ProviderRuntimeState runtimeState,
                                 boolean supportsVision, int priority, int maxRequestsPerMinute,
                                 boolean enabled) {

    /** False for a provider the router must never even attempt: no credentials, or (for CUSTOM) no
     *  USABLE base URL. This only covers "would sending a request to this provider be doomed on its
     *  face" -- combine with {@link #enabled()} for the full eligibility check.
     *  <p>
     *  CUSTOM checks {@link BaseUrlUtil#isValid}, not just non-blank (mailbox review round 035,
     *  point AB1(a)): a non-blank but syntactically malformed base URL (a hand-edited TOML value,
     *  or one saved before that GUI validation existed) is JUST as doomed as a blank one --
     *  {@code OpenAiCompatibleAdapter.resolveSpec} throws on it either way. Letting a candidate this
     *  broken pass {@code hardFilter} meant it would acquire real permits (concurrency, then both
     *  rate-limiter commits) before ever discovering the request can't be built -- see AB1(b) in
     *  {@code TranslationRouter.attemptCandidate} for the other half of that fix (request
     *  construction now happens before either rate limiter is committed regardless), but filtering
     *  this out here is strictly better: the candidate never even touches a semaphore. */
    public boolean hasCredentials() {
        if (endpoint == Config.EndPoint.OLLAMA) return true; // no key needed, local server
        if (endpoint == Config.EndPoint.CUSTOM) {
            return BaseUrlUtil.isValid(settings.customBaseUrl());
        }
        return settings.apiKey() != null && !settings.apiKey().isBlank();
    }
}
