package net.github.dctime.libs.routing;

/**
 * Structured classification of why one provider attempt failed -- adapters/HTTP layer never decide
 * fallback/cooldown themselves, they just report what happened via {@link FailureClassifier} and
 * {@link TranslationRouter} decides what to do with it. Keeps that decision in exactly one place
 * instead of scattered per-adapter special-casing.
 */
public enum ProviderFailureType {
    /** 401/403 -- invalid or missing API key. Long cooldown; only cleared by a successful Test
     *  Connection or the player actually changing the saved key (see ProviderRuntimeState). */
    AUTH,
    /** 429 -- honors a {@code Retry-After} header if present, else exponential backoff. */
    RATE_LIMIT,
    /** No HTTP response at all within the configured timeout. */
    TIMEOUT,
    /** No HTTP response at all due to a connection-level failure (DNS, refused, reset, etc.). */
    CONNECTION,
    /** 5xx. */
    SERVER,
    /** 400 -- usually NOT transient (bad model id, malformed vision request, etc.). Excludes the
     *  provider for this job only (via TranslationAttemptContext), does not cooldown the provider
     *  broadly -- a different text might work fine against the same provider/model. */
    BAD_REQUEST,
    /** The router itself determined this candidate can't serve the request (e.g. vision required,
     *  provider is text-only) -- excluded before ever building a request, never actually sent. */
    UNSUPPORTED_CAPABILITY,
    /** 2xx response whose body the adapter couldn't parse into a translation. */
    MALFORMED_RESPONSE,
    /** Any other non-2xx status code not covered above. */
    UNKNOWN,
    /** Not an HTTP outcome at all -- the eligible pool came up structurally empty because nothing
     *  configured could ever have served this job (e.g. every provider disabled, or none has
     *  credentials, in a non-SINGLE mode). Distinct on purpose from every other value here (mailbox
     *  review round 027, point V2): a null {@code finalFailure} elsewhere means "a transient timing
     *  thing, say nothing, the caller retries next frame/tick" (global budget exhaustion, or every
     *  attempt this job made was a budget skip rather than a real network call) -- but THIS is a
     *  configuration problem that will not resolve itself, and the player needs to be told, or
     *  translation silently stops with zero explanation the moment they disable their last enabled
     *  provider in Manage Providers.
     */
    NO_ELIGIBLE_PROVIDER
}
