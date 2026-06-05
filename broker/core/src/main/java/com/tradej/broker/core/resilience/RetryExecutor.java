package com.tradej.broker.core.resilience;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Combines rate limiting, retry with exponential backoff, and circuit breaking
 * into a single execution pipeline for broker REST calls.
 * <p>
 * Decision flow:
 * <ol>
 *   <li>Check circuit breaker — fast-fail if circuit is open</li>
 *   <li>Acquire rate limit token — blocks until available</li>
 *   <li>Execute the supplier</li>
 *   <li>On success → close circuit, return value</li>
 *   <li>On retryable error → backoff, retry up to {@code maxAttempts}</li>
 *   <li>On non-retryable error → rethrow immediately</li>
 *   <li>After exhausting retries → record failure on circuit, throw</li>
 * </ol>
 * <p>
 * Thread-safe.
 */
public class RetryExecutor {

    private final MultiBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public RetryExecutor(MultiBucketRateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    /**
     * Executes the supplier with retry and circuit breaking.
     *
     * @param category  rate limit category
     * @param operation unique operation name for circuit breaker isolation
     * @param policy    retry and circuit breaker policy
     * @param supplier  the operation to execute
     * @return the supplier's result
     */
    public <T> T execute(String category, String operation, RetryPolicy policy, Supplier<T> supplier) {
        circuitBreaker.assertCanExecute(operation);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                rateLimiter.acquire(category);
                T value = supplier.get();
                circuitBreaker.onSuccess(operation);
                return value;
            } catch (RuntimeException ex) {
                BrokerErrorCategory cat = classify(ex);
                if (!BrokerErrorCategory.isRetryable(cat)) {
                    throw ex;
                }
                lastFailure = ex;
                if (attempt < policy.maxAttempts()) {
                    sleepBackoff(attempt, policy);
                }
            }
        }
        circuitBreaker.onFailure(operation, policy.circuitBreakAfterFailures(), policy.circuitOpenMs());
        throw new RuntimeException("Operation failed after " + policy.maxAttempts() + " attempts: " + operation, lastFailure);
    }

    /** Convenience method for void operations. */
    public void run(String category, String operation, RetryPolicy policy, Runnable action) {
        execute(category, operation, policy, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    private static void sleepBackoff(int attempt, RetryPolicy policy) {
        long delayMs = BackoffStrategy.computeDelayMs(attempt, policy.baseDelayMs(), policy.maxDelayMs());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while backing off before retry", ex);
        }
    }

    /**
     * Classifies an exception into a {@link BrokerErrorCategory}.
     * Broker adapter-specific subclasses may override this to provide
     * broker-specific error classification.
     */
    protected BrokerErrorCategory classify(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException) {
            return BrokerErrorCategory.VALIDATION_ERROR;
        }
        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("429")) {
                return BrokerErrorCategory.RATE_LIMITED;
            }
            if (message.contains("503") || message.contains("502") || message.contains("500")) {
                return BrokerErrorCategory.SERVICE_DOWN;
            }
        }
        return BrokerErrorCategory.UNKNOWN;
    }
}
