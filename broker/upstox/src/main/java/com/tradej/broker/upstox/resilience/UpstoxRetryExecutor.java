package com.tradej.broker.upstox.resilience;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.CircuitBreakerConfig;
import com.tradej.broker.core.resilience.RetryExecutor;
import com.tradej.broker.core.resilience.RetryPolicy;
import com.tradej.broker.upstox.http.UpstoxApiException;

/**
 * Upstox-specific {@link RetryExecutor} with {@link UpstoxApiException} classification.
 */
public final class UpstoxRetryExecutor extends RetryExecutor {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    public static final String CATEGORY_EXPIRED_INSTRUMENT = "EXPIRED_INSTRUMENT";
    public static final String CATEGORY_DATA = "DATA";

    public static final RetryPolicy EXPIRED_INSTRUMENT_POLICY =
            new RetryPolicy(3, 500L, 5_000L, 5, 30_000L);
    public static final RetryPolicy DATA_POLICY =
            new RetryPolicy(3, 500L, 5_000L, 5, 30_000L);

    public UpstoxRetryExecutor(MultiBucketRateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        super(rateLimiter, circuitBreaker);
    }

    /**
     * Creates an {@code UpstoxRetryExecutor} with a {@link CircuitBreakerConfig#DEFAULT}
     * circuit breaker.
     *
     * @param rateLimiter rate limiter for Upstox API calls
     */
    public UpstoxRetryExecutor(MultiBucketRateLimiter rateLimiter) {
        super(rateLimiter, CircuitBreakerConfig.DEFAULT);
    }

    @Override
    protected BrokerErrorCategory classify(RuntimeException ex) {
        if (ex instanceof UpstoxApiException api) {
            if (api.isAuthFailure()) {
                return BrokerErrorCategory.AUTH_REVOKED;
            }
            if (api.httpStatus() == HTTP_TOO_MANY_REQUESTS) {
                return BrokerErrorCategory.RATE_LIMITED;
            }
            if (api.httpStatus() >= 500) {
                return BrokerErrorCategory.SERVICE_DOWN;
            }
            if (api.httpStatus() >= 400) {
                return BrokerErrorCategory.VALIDATION_ERROR;
            }
        }
        return super.classify(ex);
    }
}
