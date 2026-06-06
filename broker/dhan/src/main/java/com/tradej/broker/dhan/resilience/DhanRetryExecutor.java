package com.tradej.broker.dhan.resilience;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.CircuitBreakerConfig;
import com.tradej.broker.core.resilience.RetryExecutor;
import com.tradej.broker.core.resilience.RetryPolicy;
import com.tradej.broker.dhan.auth.DhanAuthenticationException;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.rate.ApiCategory;

import java.util.function.Supplier;

/**
 * Dhan-specific {@link RetryExecutor} with {@link DhanAuthenticationException} classification
 * and {@link ApiCategory}-based convenience methods.
 */
public final class DhanRetryExecutor extends RetryExecutor {

    public DhanRetryExecutor(MultiBucketRateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        super(rateLimiter, circuitBreaker);
    }

    /**
     * Creates a {@code DhanRetryExecutor} with an {@link CircuitBreakerConfig#AGGRESSIVE}
     * circuit breaker, suitable for Dhan's strict rate limits.
     *
     * @param rateLimiter rate limiter for Dhan API calls
     */
    public DhanRetryExecutor(MultiBucketRateLimiter rateLimiter) {
        super(rateLimiter, CircuitBreakerConfig.AGGRESSIVE);
    }

    public <T> T execute(ApiCategory category, String operation, Supplier<T> supplier) {
        return super.execute(category.name(), operation, policyFor(category), supplier);
    }

    public void run(ApiCategory category, String operation, Runnable action) {
        execute(category, operation, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    @Override
    protected BrokerErrorCategory classify(RuntimeException ex) {
        if (ex instanceof DhanAuthenticationException) {
            return BrokerErrorCategory.AUTH_REVOKED;
        }
        return super.classify(ex);
    }

    private static RetryPolicy policyFor(ApiCategory category) {
        int maxAttempts = category == ApiCategory.ORDER
                ? DhanProtocolConstants.RETRY_COUNT_ORDER
                : DhanProtocolConstants.RETRY_COUNT_DEFAULT;
        return new RetryPolicy(
                maxAttempts,
                DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                DhanProtocolConstants.RETRY_MAX_DELAY_MS,
                DhanProtocolConstants.RETRY_FAILURE_THRESHOLD,
                DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS);
    }
}
