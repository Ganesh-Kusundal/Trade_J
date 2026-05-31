package com.tradej.broker.core.resilience;

/**
 * Configuration for a single retryable operation category.
 *
 * @param maxAttempts    maximum number of retry attempts (including the first call)
 * @param baseDelayMs    base delay for exponential backoff in milliseconds
 * @param maxDelayMs     maximum delay cap in milliseconds
 * @param circuitBreakAfterFailures consecutive failures before opening the circuit
 * @param circuitOpenMs  duration in milliseconds the circuit stays open
 */
public record RetryPolicy(
        int maxAttempts,
        long baseDelayMs,
        long maxDelayMs,
        int circuitBreakAfterFailures,
        long circuitOpenMs
) {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (baseDelayMs < 1) {
            throw new IllegalArgumentException("baseDelayMs must be >= 1");
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= baseDelayMs");
        }
        if (circuitBreakAfterFailures < 1) {
            throw new IllegalArgumentException("circuitBreakAfterFailures must be >= 1");
        }
        if (circuitOpenMs < 1) {
            throw new IllegalArgumentException("circuitOpenMs must be >= 1");
        }
    }
}
