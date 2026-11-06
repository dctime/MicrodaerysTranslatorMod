package net.github.dctime.libs;

/**
 * Pure retry/backoff math for the translation request throttling in {@link Translator}. No
 * Minecraft/NeoForge dependency on purpose, so it can be unit-tested (see
 * tools/verify-concurrency) without a game runtime -- same idea as {@link JsonUtil}.
 */
public class RetryPolicy {

    /** Exponential backoff after a 429: 4s, 8s, 16s, then capped at 30s. */
    public static long backoffDelayMs(int attempt) {
        return Math.min(30_000L, 2000L * (1L << Math.min(attempt, 4)));
    }
}
