package com.tradej.broker.icici.resilience;

import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.BackoffStrategy;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.CircuitBreakerConfig;
import com.tradej.broker.core.resilience.RetryPolicy;
import com.tradej.broker.icici.http.BreezeHttpException;

import java.util.function.Supplier;

public final class IciciResilienceExecutor {

    public static final String CATEGORY_ORDER = "ORDER";
    public static final String CATEGORY_DATA = "DATA";
    public static final String CATEGORY_DAILY = "DAILY";

    private static final RetryPolicy DATA_POLICY =
            new RetryPolicy(3, 500L, 5_000L, 5, 30_000L);

    private final MultiBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public IciciResilienceExecutor(MultiBucketRateLimiter rateLimiter) {
        this(rateLimiter, new CircuitBreaker(CircuitBreakerConfig.CONSERVATIVE));
    }

    public IciciResilienceExecutor(MultiBucketRateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    public <T> T executeData(String operation, Supplier<T> supplier) {
        return execute(CATEGORY_DATA, operation, supplier);
    }

    public <T> T executeDaily(String operation, Supplier<T> supplier) {
        return execute(CATEGORY_DAILY, operation, supplier);
    }

    public <T> T executeOrder(String operation, Supplier<T> supplier) {
        return execute(CATEGORY_ORDER, operation, supplier);
    }

    private <T> T execute(String category, String operation, Supplier<T> supplier) {
        circuitBreaker.assertCanExecute(operation);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= DATA_POLICY.maxAttempts(); attempt++) {
            try {
                rateLimiter.acquire(category);
                T value = supplier.get();
                circuitBreaker.onSuccess(operation);
                if (lastFailure != null) {
                    onSuccess(category);
                }
                return value;
            } catch (RuntimeException ex) {
                if (!isRetryable(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                if (ex instanceof BreezeHttpException breeze && breeze.httpStatus() == 429) {
                    onRateLimitResponse(category);
                }
                if (attempt < DATA_POLICY.maxAttempts()) {
                    sleepBackoff(attempt);
                }
            }
        }
        circuitBreaker.onFailure(operation, DATA_POLICY.circuitBreakAfterFailures(), DATA_POLICY.circuitOpenMs());
        throw new RuntimeException(
                "ICICI operation failed after " + DATA_POLICY.maxAttempts() + " attempts: " + operation,
                lastFailure
        );
    }

    /**
     * Reduce rate on 429 to prevent repeated throttling.
     * Multiplicative decrease: halve the fill rate for the bucket.
     */
    private void onRateLimitResponse(String category) {
        try {
            rateLimiter.reduceRate(category, 0.5);
        } catch (Exception ignored) {
        }
    }

    /**
     * Gradually restore rate after successful call following a throttle.
     * Additive increase: +10% per success.
     */
    private void onSuccess(String category) {
        try {
            rateLimiter.increaseRate(category, 0.1);
        } catch (Exception ignored) {
        }
    }

    private static boolean isRetryable(RuntimeException ex) {
        if (ex instanceof BreezeHttpException breeze) {
            return breeze.httpStatus() == 429 || breeze.httpStatus() >= 500;
        }
        return ex instanceof IllegalStateException;
    }

    private static void sleepBackoff(int attempt) {
        long delayMs = BackoffStrategy.computeDelayMs(attempt, DATA_POLICY.baseDelayMs(), DATA_POLICY.maxDelayMs());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying ICICI operation", ex);
        }
    }
}
