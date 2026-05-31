package com.tradej.broker.icici.resilience;

import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.BackoffStrategy;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.RetryPolicy;
import com.tradej.broker.icici.http.BreezeHttpException;

import java.util.function.Supplier;

public final class IciciResilienceExecutor {

    public static final String CATEGORY_DATA = "DATA";
    public static final String CATEGORY_DAILY = "DAILY";

    private static final RetryPolicy DATA_POLICY =
            new RetryPolicy(3, 500L, 5_000L, 5, 30_000L);

    private final MultiBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public IciciResilienceExecutor(MultiBucketRateLimiter rateLimiter) {
        this(rateLimiter, new CircuitBreaker());
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

    private <T> T execute(String category, String operation, Supplier<T> supplier) {
        circuitBreaker.assertCanExecute(operation);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= DATA_POLICY.maxAttempts(); attempt++) {
            try {
                rateLimiter.acquire(category);
                T value = supplier.get();
                circuitBreaker.onSuccess(operation);
                return value;
            } catch (RuntimeException ex) {
                if (!isRetryable(ex)) {
                    throw ex;
                }
                lastFailure = ex;
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
