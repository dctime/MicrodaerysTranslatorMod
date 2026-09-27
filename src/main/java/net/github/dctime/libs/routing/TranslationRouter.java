package net.github.dctime.libs.routing;

import net.github.dctime.Config;
import net.github.dctime.libs.RateLimiter;
import net.github.dctime.libs.RetryPolicy;

import javax.annotation.Nullable;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks ONE provider per translation job and sends it -- never parallel/racing (explicitly out of
 * scope, per the spec). Fallback across providers happens only on a transient failure (429/
 * timeout/connection/5xx), governed by the current {@link ProviderMode}. Owns the actual HTTP send
 * from this round onward -- {@code Translator} builds a {@link TranslationJob} and calls {@link
 * #translate}, it never talks to {@code HttpClient} directly any more (see that class's own
 * javadoc for the division of responsibility: cache/IN_FLIGHT/prompt-resolution/tooltip-Jade-FTB
 * flow stay there, provider selection and the request round-trip move here).
 * <p>
 * Recursive async attempt loop -- {@link #translate} returns immediately, {@link #attemptNext}
 * recurses via {@code CompletableFuture.whenComplete} callbacks, never {@code future.get()} or
 * {@code Thread.sleep()}. Every expected failure path (pool exhausted, budget exhausted, every
 * candidate tried and failed) completes the returned future NORMALLY with a clean-failure {@link
 * TranslationResult} -- exceptional completion is reserved for bugs that should never happen in a
 * tested path.
 * <p>
 * Concurrency/rate permits are acquired and released PER ATTEMPT, not per job (mailbox review round
 * 023/024, point R3): since attempts within one job are strictly sequential, a job never holds more
 * than one global slot at any instant, which is what makes "fallback doesn't consume a second
 * global slot" true without needing to hold a permit across a job's whole (possibly multi-attempt)
 * lifetime.
 */
public final class TranslationRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationRouter.class);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // A hard ceiling independent of how many providers are enabled -- prevents N enabled providers
    // each with their own concurrency budget from summing into an unbounded burst (spec's explicit
    // "保留一個 Global safety cap"). Deliberately NOT exposed to the player this round, matching the
    // per-provider concurrency default's own "not configurable yet" scope. Public (not player-
    // configurable, but not private either) so tools/verify-concurrency can assert against the real
    // constant instead of an independently-hardcoded copy that could silently drift from it.
    public static final int GLOBAL_CONCURRENCY_LIMIT = 8;
    private static final Semaphore GLOBAL_CONCURRENCY = new Semaphore(GLOBAL_CONCURRENCY_LIMIT);

    // Config.MAX_REQUESTS_PER_MINUTE is repurposed as this: a ceiling ON TOP OF the new per-provider
    // RPM limits, not the only limiter any more -- semantics preserved (still "requests per rolling
    // 60s, across everything"), just layered differently now that per-provider limits also exist.
    private static final RateLimiter GLOBAL_RATE_LIMITER = new RateLimiter(60_000L);

    private static final RoutingStrategy SINGLE_STRATEGY = new SingleRoutingStrategy();
    private static final RoutingStrategy PRIORITY_STRATEGY = new PriorityRoutingStrategy();
    private static final RoutingStrategy ROUND_ROBIN_STRATEGY = new RoundRobinRoutingStrategy();
    private static final RoutingStrategy AUTOMATIC_STRATEGY = new AutomaticRoutingStrategy();

    private TranslationRouter() {
    }

    public static CompletableFuture<TranslationResult> translate(TranslationJob job) {
        CompletableFuture<TranslationResult> result = new CompletableFuture<>();
        long jobStartMillis = System.currentTimeMillis();
        attemptNext(job, new TranslationAttemptContext(), jobStartMillis, result);
        return result;
    }

    private static void attemptNext(TranslationJob job, TranslationAttemptContext context,
                                     long jobStartMillis, CompletableFuture<TranslationResult> result) {
        if (context.reachedMaxAttempts()) {
            result.complete(TranslationResult.failure(context.lastFailureType()));
            return;
        }

        ProviderMode mode = Config.PROVIDER_MODE.get();
        List<ProviderCandidate> rawPool = mode == ProviderMode.SINGLE
                ? List.of(ProviderPool.buildCandidate(Config.ENDPOINT_CONFIG.get()))
                : ProviderPool.all();

        List<ProviderCandidate> eligible = hardFilter(rawPool, job, context, mode);

        if (eligible.isEmpty()) {
            boolean anyRawCandidateSupportsVision = rawPool.stream().anyMatch(ProviderCandidate::supportsVision);
            boolean anyRawCandidateEnabledWithCredentials = rawPool.stream().anyMatch(c -> c.enabled() && c.hasCredentials());
            ProviderFailureType failure = EmptyPoolFailureClassifier.classify(job.visionRequirement(),
                    anyRawCandidateSupportsVision, mode, anyRawCandidateEnabledWithCredentials, context.lastFailureType());
            result.complete(TranslationResult.failure(failure));
            return;
        }

        List<ProviderCandidate> ranked = strategyFor(mode).rank(eligible);
        attemptCandidate(job, ranked.get(0), context, jobStartMillis, result);
    }

    /** Every strategy only orders an already-eligible list -- this is where "eligible" gets decided
     *  (spec's explicit "先 hard filter，再 ranking" split, see {@link RoutingStrategy}'s javadoc).
     *  SINGLE mode skips the enabled/credentials checks on purpose: it forces the pool down to
     *  exactly {@code Config.ENDPOINT_CONFIG}'s provider regardless of its enabled flag or whether
     *  it has a saved key, matching the pre-Router code's exact behavior of attempting the request
     *  and letting a real 401 come back rather than silently refusing to try -- the ONE thing SINGLE
     *  mode still filters is a cooldown (replaces the old text-keyed RETRY_AFTER backoff, correctly
     *  scoped to the provider now) and a hard vision-requirement mismatch (matches the pre-existing
     *  vision gate in {@code Translator}, which already refused to send a doomed image-required
     *  request to a text-only model). */
    private static List<ProviderCandidate> hardFilter(List<ProviderCandidate> pool, TranslationJob job,
                                                        TranslationAttemptContext context, ProviderMode mode) {
        long now = System.currentTimeMillis();
        List<ProviderCandidate> eligible = new ArrayList<>();
        for (ProviderCandidate candidate : pool) {
            if (context.hasAttempted(candidate.endpoint())) continue;
            if (mode != ProviderMode.SINGLE && !candidate.enabled()) continue;
            if (mode != ProviderMode.SINGLE && !candidate.hasCredentials()) continue;
            if (job.visionRequirement() == VisionRequirement.REQUIRED && !candidate.supportsVision()) continue;
            if (candidate.runtimeState().status(now) != ProviderRuntimeState.Status.AVAILABLE) continue;
            eligible.add(candidate);
        }
        return eligible;
    }

    private static RoutingStrategy strategyFor(ProviderMode mode) {
        return switch (mode) {
            case SINGLE -> SINGLE_STRATEGY;
            case PRIORITY -> PRIORITY_STRATEGY;
            case ROUND_ROBIN -> ROUND_ROBIN_STRATEGY;
            case AUTOMATIC -> AUTOMATIC_STRATEGY;
        };
    }

    /** Acquires this attempt's permits, sends the request, and always releases everything for this
     *  attempt in {@code whenComplete} before either recursing to the next candidate or completing
     *  the job. Global-level exhaustion completes the job immediately (matches the pre-Router code's
     *  own "dropped, not queued -- caller retries on a later frame" semantics: trying a different
     *  candidate wouldn't help, every candidate shares the same global permits). Provider-level
     *  exhaustion is scoped to just this candidate -- released, marked attempted, and the loop tries
     *  the next ranked candidate immediately (acceptance tests 3/20/21).
     *  <p>
     *  <b>The general rule, found the hard way across three live-playtest bugs in the same session
     *  (mailbox review rounds 034/035, points AA1 and AB1, plus the original fix they both extend):
     *  </b> acquire every REVOCABLE resource first (a {@code Semaphore} permit, which {@code
     *  release()} can give back if a later check fails), and resolve every OTHER gate that can still
     *  fail (building the request is pure computation, but a malformed Custom Provider base URL can
     *  still throw -- see AB1(a)/(b) below) BEFORE consuming a NON-REVOCABLE resource (a {@link
     *  RateLimiter} slot -- once {@code tryAcquire} returns true it has already recorded the
     *  timestamp, with no "undo"). With exactly ONE non-revocable resource in the chain, simply
     *  ordering it last is enough (the first bug's fix: concurrency before rate). With TWO OR MORE
     *  non-revocable resources (global rate AND provider rate), ordering can't help -- whichever one
     *  is checked second still gets to consume the first one's slot on an attempt that's doomed to
     *  fail at the second check; the fix there (AA1) is CHECK-THEN-COMMIT: peek every non-revocable
     *  resource with a non-consuming read ({@link ProviderRuntimeState#hasRateBudget} here), and
     *  only actually consume ANY of them once every one of them has already confirmed it would
     *  succeed. Request construction (AB1(b)) extends the SAME rule one step further: it's yet
     *  another gate that can fail (independent of {@link ProviderCandidate#hasCredentials()}'s own
     *  AB1(a) fix, which already filters out the one known-malformed-base-URL case at {@code
     *  hardFilter} time -- defense in depth, not the only line), so it now runs BEFORE either rate
     *  limiter is committed, not after. Peek-then-commit leaves a narrow (single-thread, no I/O,
     *  microseconds) check-then-act race between the peek and the commit -- worst case an occasional
     *  extra admission (or, on the rare unlucky interleaving where global commits but provider's
     *  commit then loses the race, one genuinely wasted global-rate slot -- not a permit leak, but a
     *  real, if vanishingly rare, rate-slot cost), never the original bug's "burn an entire 60s
     *  budget on requests that were never sent". */
    private static void attemptCandidate(TranslationJob job, ProviderCandidate candidate,
                                          TranslationAttemptContext context, long jobStartMillis,
                                          CompletableFuture<TranslationResult> result) {
        long now = System.currentTimeMillis();

        if (!GLOBAL_CONCURRENCY.tryAcquire()) {
            result.complete(TranslationResult.failure(context.lastFailureType()));
            return;
        }

        ProviderRuntimeState state = candidate.runtimeState();

        if (!state.concurrencyLimit().tryAcquire()) {
            GLOBAL_CONCURRENCY.release();
            context.markAttempted(candidate.endpoint());
            attemptNext(job, context, jobStartMillis, result);
            return;
        }

        // Peek BOTH non-revocable resources before committing to EITHER (see this method's own
        // javadoc, point AA1) -- whichever one lacks budget still decides the same failure mode
        // (global -> terminate the whole job; provider -> scoped to this candidate) as if it had
        // been checked and consumed directly, just without ever touching the other one first.
        if (!hasGlobalRateBudget(now)) {
            state.concurrencyLimit().release();
            GLOBAL_CONCURRENCY.release();
            result.complete(TranslationResult.failure(context.lastFailureType()));
            return;
        }
        if (!state.hasRateBudget(candidate.maxRequestsPerMinute(), now)) {
            state.concurrencyLimit().release();
            GLOBAL_CONCURRENCY.release();
            context.markAttempted(candidate.endpoint());
            attemptNext(job, context, jobStartMillis, result);
            return;
        }

        // Build the request BEFORE committing either rate limiter (mailbox review round 035, point
        // AB1(b)): this is pure computation, no network, but it CAN still throw (a malformed Custom
        // Provider base URL that slipped past hasCredentials()'s own AB1(a) fix, e.g. a value saved
        // before that fix existed). If it throws here, only the two (revocable) concurrency permits
        // need releasing -- neither rate limiter has been touched yet, so nothing non-revocable is
        // ever wasted on a request that was never going to be sendable in the first place.
        HttpRequest request;
        String image;
        try {
            image = imageForAttempt(job, candidate);
            request = candidate.adapter().buildTranslationRequest(candidate.settings(), job.prompt(),
                    image, job.isScreenshot(), Config.TIMEOUT_DURATION_CONFIG.get());
        } catch (Exception e) {
            LOGGER.warn("Failed to build translation request for provider {}: {}", candidate.endpoint(), e.getMessage());
            state.concurrencyLimit().release();
            GLOBAL_CONCURRENCY.release();
            state.recordNonTransientFailure();
            context.markAttempted(candidate.endpoint());
            context.recordFailure(ProviderFailureType.BAD_REQUEST);
            attemptNext(job, context, jobStartMillis, result);
            return;
        }

        // Everything that could still fail before actually sending has now been ruled out -- commit
        // both non-revocable resources. A concurrent attempt on another candidate/thread could
        // theoretically consume a slot in between the earlier peek and this commit (the narrow race
        // this method's own javadoc accepts); either commit failing here is treated the same as the
        // corresponding peek failing, just one attempt later than ideal.
        if (!tryAcquireGlobalRate(now)) {
            state.concurrencyLimit().release();
            GLOBAL_CONCURRENCY.release();
            result.complete(TranslationResult.failure(context.lastFailureType()));
            return;
        }
        if (!state.tryAcquireRate(candidate.maxRequestsPerMinute(), now)) {
            state.concurrencyLimit().release();
            GLOBAL_CONCURRENCY.release();
            context.markAttempted(candidate.endpoint());
            attemptNext(job, context, jobStartMillis, result);
            return;
        }

        state.incrementInFlight();

        // temporary diagnostic logging (see the matching [DIAG] logs in handleResponse below) --
        // deliberate LOGGER.info, not .debug: NeoForge hides .debug by default, and this is the one
        // log line that answers "translation came back in English" reports by showing exactly which
        // provider/model/prompt was actually used for an attempt, now that more than one provider
        // can be tried per job. Never logs the API key (settings.apiKey() is never referenced here).
        // Deliberately logged HERE, after both rate limiters are committed, not right after building
        // the request -- this line means "this request IS being sent", which is only true once every
        // gate that could still have stopped it has already passed.
        LOGGER.info("[DIAG] translation request: endpoint=" + candidate.endpoint() + " model="
                + candidate.settings().model() + " url=" + request.uri() + " hasImage=" + (image != null)
                + " prompt=[" + job.prompt() + "]");

        // Everything from here down that can throw MUST release the same three permits on failure
        // (mailbox review round 027, point V1): once incrementInFlight()/the commits above have
        // succeeded, a throw that escapes this method without releasing them would leak both
        // permits, leave inFlight permanently inflated, and (once Translator delegates to this
        // class) never complete the returned future at all -- the exact "acquire/release pairing has
        // no automated test to catch it" failure mode this file's own comments already warn about
        // elsewhere. A synchronous sendAsync failure (e.g. a RejectedExecutionException from an
        // already-shut-down executor) is classified CONNECTION -- a real transient/infra signal,
        // cooldown applies (distinct from the BAD_REQUEST catch above, which is job-scoped only: the
        // request itself could never have been well-formed, that says nothing about the provider's
        // general health).
        long attemptStartMillis = System.currentTimeMillis();
        try {
            // .whenComplete(...) is chained onto sendAsync's OWN return value in this one
            // expression -- if sendAsync throws synchronously (the catch below), that chain was
            // never built and the callback was never registered, so there is no risk of this catch's
            // cleanup and the callback's cleanup both running for the same attempt (mailbox review
            // round 028: this reasoning belongs on the code, not just in a mailbox reply).
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((resp, throwable) -> {
                        state.decrementInFlight();
                        state.concurrencyLimit().release();
                        GLOBAL_CONCURRENCY.release();

                        if (throwable != null) {
                            ProviderFailureType type = FailureClassifier.classifyThrowable(throwable);
                            state.recordTransientFailure(type, null);
                            context.markAttempted(candidate.endpoint());
                            context.recordFailure(type);
                            attemptNext(job, context, jobStartMillis, result);
                            return;
                        }

                        long latencyMs = System.currentTimeMillis() - attemptStartMillis;
                        handleResponse(job, candidate, context, jobStartMillis, resp, latencyMs, result);
                    });
        } catch (Exception e) {
            LOGGER.warn("Failed to send translation request for provider {}: {}", candidate.endpoint(), e.getMessage());
            releaseAcquiredPermits(state);
            state.recordTransientFailure(ProviderFailureType.CONNECTION, null);
            context.markAttempted(candidate.endpoint());
            context.recordFailure(ProviderFailureType.CONNECTION);
            attemptNext(job, context, jobStartMillis, result);
        }
    }

    private static void releaseAcquiredPermits(ProviderRuntimeState state) {
        state.decrementInFlight();
        state.concurrencyLimit().release();
        GLOBAL_CONCURRENCY.release();
    }

    private static void handleResponse(TranslationJob job, ProviderCandidate candidate,
                                        TranslationAttemptContext context, long jobStartMillis,
                                        HttpResponse<String> resp, long latencyMs,
                                        CompletableFuture<TranslationResult> result) {
        ProviderRuntimeState state = candidate.runtimeState();

        // temporary diagnostic logging, see the matching [DIAG] log in attemptCandidate above.
        LOGGER.info("[DIAG] response from " + candidate.endpoint() + " status=" + resp.statusCode()
                + " body=[" + resp.body() + "]");

        if (resp.statusCode() / 100 == 2) {
            String translatedText;
            try {
                translatedText = candidate.adapter().parseTranslationResponse(resp.body());
            } catch (Exception e) {
                LOGGER.warn("Malformed response from provider {}: {}", candidate.endpoint(), e.getMessage());
                translatedText = null;
            }
            LOGGER.info("[DIAG] parsed translatedText=[" + translatedText + "]");

            if (translatedText == null || translatedText.isBlank()) {
                // A 2xx with no usable content -- job-scoped only (no cooldown), matches
                // MALFORMED_RESPONSE's own scope, same as the try/catch above.
                state.recordNonTransientFailure();
                context.markAttempted(candidate.endpoint());
                context.recordFailure(ProviderFailureType.MALFORMED_RESPONSE);
                attemptNext(job, context, jobStartMillis, result);
                return;
            }

            state.recordSuccess(latencyMs);
            long totalLatencyMs = System.currentTimeMillis() - jobStartMillis;
            result.complete(TranslationResult.success(translatedText, candidate.endpoint(), totalLatencyMs));
            return;
        }

        ProviderFailureType type = FailureClassifier.classifyHttpStatus(resp.statusCode());
        Long retryAfterMillis = parseRetryAfterMillis(resp);

        switch (type) {
            case AUTH -> state.recordAuthFailure();
            case RATE_LIMIT, TIMEOUT, CONNECTION, SERVER -> state.recordTransientFailure(type, retryAfterMillis);
            default -> state.recordNonTransientFailure(); // BAD_REQUEST/UNKNOWN -- job-scoped only
        }

        LOGGER.debug("Provider {} failed with status {}, classified as {}", candidate.endpoint(),
                resp.statusCode(), type);

        context.markAttempted(candidate.endpoint());
        context.recordFailure(type);
        attemptNext(job, context, jobStartMillis, result);
    }

    /** Only the numeric-seconds form of {@code Retry-After} is honored -- the HTTP-date form is
     *  valid per spec but none of the 11 providers' documented 429 responses use it; falling back
     *  to {@link RetryPolicy#backoffDelayMs} (unparseable/absent) is always safe either way. */
    @Nullable
    private static Long parseRetryAfterMillis(HttpResponse<String> resp) {
        return resp.headers().firstValue("Retry-After").map(value -> {
            try {
                return Long.parseLong(value.trim()) * 1000L;
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    /** {@link VisionRequirement#NONE}: never attach an image. {@link VisionRequirement#REQUIRED}:
     *  the candidate is already guaranteed vision-capable by {@link #hardFilter}, so always attach.
     *  {@link VisionRequirement#OPTIONAL}: attach only if THIS specific candidate supports it --
     *  other candidates remain eligible, they just get the request built without the image (matches
     *  the pre-existing item-icon behavior in {@code Translator}: no error, silent text-only
     *  degrade). */
    @Nullable
    private static String imageForAttempt(TranslationJob job, ProviderCandidate candidate) {
        if (job.visionRequirement() == VisionRequirement.NONE) return null;
        if (job.visionRequirement() == VisionRequirement.REQUIRED) return job.imageBase64();
        return candidate.supportsVision() ? job.imageBase64() : null;
    }

    /** Synchronized for the same reason {@link ProviderRuntimeState}'s rate-limiter accessors are:
     *  {@link RateLimiter} isn't thread-safe by itself, and this global instance is now touched from
     *  HTTP completion callback threads via the fallback loop, not just one caller at a time. */
    private static synchronized boolean tryAcquireGlobalRate(long nowMillis) {
        return GLOBAL_RATE_LIMITER.tryAcquire(Config.MAX_REQUESTS_PER_MINUTE.get(), nowMillis);
    }

    /** Non-consuming peek, mirroring {@link ProviderRuntimeState#hasRateBudget} -- see {@link
     *  #attemptCandidate}'s javadoc (point AA1) for why a non-revocable resource must be peeked
     *  before ANY non-revocable resource in the same attempt is committed. */
    private static synchronized boolean hasGlobalRateBudget(long nowMillis) {
        return GLOBAL_RATE_LIMITER.usageAt(nowMillis) < Config.MAX_REQUESTS_PER_MINUTE.get();
    }
}
