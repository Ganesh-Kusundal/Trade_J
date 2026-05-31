package com.tradej.broker.upstox.resilience;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.BackoffStrategy;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.RetryPolicy;
import com.tradej.broker.upstox.http.UpstoxApiException;

import java.util.function.Supplier;

public final class UpstoxResilienceExecutor {

    public static final String CATEGORY_EXPIRED_INSTRUMENT = "EXPIRED_INSTRUMENT";
    public static final String CATEGORY_DATA = "DATA";

    private static final RetryPolicy EXPIRED_INSTRUMENT_POLICY =
            new RetryPolicy(3, 500L, 5_000L, 5, 30_000L);
    private static final RetryPolicy DATA_POLICY =
            new RetryPolicy(3, 500L, 5_000L, 5, 30_000L);

    private final MultiBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public UpstoxResilienceExecutor(MultiBucketRateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    public <T> T executeExpiredInstrument(String operation, Supplier<T> supplier) {
        return execute(CATEGORY_EXPIRED_INSTRUMENT, operation, EXPIRED_INSTRUMENT_POLICY, supplier);
    }

    public <T> T executeData(String operation, Supplier<T> supplier) {
        return execute(CATEGORY_DATA, operation, DATA_POLICY, supplier);
    }

    private <T> T execute(String category, String operation, RetryPolicy policy, Supplier<T> supplier) {
        circuitBreaker.assertCanExecute(operation);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                rateLimiter.acquire(category);
                T value = supplier.get();
                circuitBreaker.onSuccess(operation);
                return value;
            } catch (RuntimeException ex) {
                BrokerErrorCategory errorCategory = classify(ex);
                if (!BrokerErrorCategory.isRetryable(errorCategory)) {
                    throw ex;
                }
                lastFailure = ex;
                if (attempt < policy.maxAttempts()) {
                    sleepBackoff(attempt, policy);
                }
            }
        }
        circuitBreaker.onFailure(operation, policy.circuitBreakAfterFailures(), policy.circuitOpenMs());
        throw new RuntimeException(
                "Upstox operation failed after " + policy.maxAttempts() + " attempts: " + operation,
                lastFailure
        );
    }

    private static BrokerErrorCategory classify(RuntimeException ex) {
        if (ex instanceof UpstoxApiException api) {
            if (api.isAuthFailure()) {
                return BrokerErrorCategory.AUTH_REVOKED;
            }
            if (api.httpStatus() == 429) {
                return BrokerErrorCategory.RATE_LIMITED;
            }
            if (api.httpStatus() >= 500) {
                return BrokerErrorCategory.SERVICE_DOWN;
            }
            if (api.httpStatus() >= 400) {
                return BrokerErrorCategory.VALIDATION_ERROR;
            }
        }
        return BrokerErrorCategory.UNKNOWN;
    }

    private static void sleepBackoff(int attempt, RetryPolicy policy) {
        long delayMs = BackoffStrategy.computeDelayMs(attempt, policy.baseDelayMs(), policy.maxDelayMs());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Upstox operation", ex);
        }
    }
}
