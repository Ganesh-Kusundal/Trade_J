package com.tradej.broker.dhan.resilience;

import com.tradej.broker.core.resilience.BackoffStrategy;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.RetryPolicy;
import com.tradej.broker.dhan.auth.DhanAuthenticationException;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.rate.MultiBucketRateLimiter;

import java.util.function.Supplier;

/**
 * Combined circuit breaker + retry + rate limiting for Dhan REST calls.
 * <p>
 * Delegates backoff to {@link BackoffStrategy} and circuit breaking to
 * {@link CircuitBreaker} from {@code trade-broker-core}.
 *
 * @deprecated Will be replaced by direct use of {@code com.tradej.broker.core.resilience.RetryExecutor}
 *             in a future release.
 */
@Deprecated
public final class DhanResilienceExecutor {
    private final MultiBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public DhanResilienceExecutor(MultiBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = new CircuitBreaker();
    }

    public <T> T execute(ApiCategory category, String operation, Supplier<T> supplier) {
        circuitBreaker.assertCanExecute(operation);
        RetryPolicy policy = policyFor(category);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                rateLimiter.acquire(category);
                T value = supplier.get();
                circuitBreaker.onSuccess(operation);
                return value;
            } catch (DhanAuthenticationException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt < policy.maxAttempts()) {
                    sleepBackoff(attempt, policy);
                }
            }
        }
        circuitBreaker.onFailure(operation,
                policy.circuitBreakAfterFailures(), policy.circuitOpenMs());
        throw new DhanHttpException("Dhan operation failed after " + policy.maxAttempts()
                + " attempts: " + operation, lastFailure);
    }

    public void run(ApiCategory category, String operation, Runnable action) {
        execute(category, operation, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    private static RetryPolicy policyFor(ApiCategory category) {
        return switch (category) {
            case ORDER -> new RetryPolicy(
                    DhanProtocolConstants.RETRY_COUNT_ORDER,
                    DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                    DhanProtocolConstants.RETRY_MAX_DELAY_MS,
                    DhanProtocolConstants.RETRY_FAILURE_THRESHOLD,
                    DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS);
            case DATA -> new RetryPolicy(
                    DhanProtocolConstants.RETRY_COUNT_DATA,
                    DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                    DhanProtocolConstants.RETRY_MAX_DELAY_MS,
                    DhanProtocolConstants.RETRY_FAILURE_THRESHOLD,
                    DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS);
            case QUOTE -> new RetryPolicy(
                    DhanProtocolConstants.RETRY_COUNT_QUOTE,
                    DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                    DhanProtocolConstants.RETRY_MAX_DELAY_MS,
                    DhanProtocolConstants.RETRY_FAILURE_THRESHOLD,
                    DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS);
            case OPTION_CHAIN -> new RetryPolicy(
                    DhanProtocolConstants.RETRY_COUNT_OPTION_CHAIN,
                    DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                    DhanProtocolConstants.RETRY_MAX_DELAY_MS,
                    DhanProtocolConstants.RETRY_FAILURE_THRESHOLD,
                    DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS);
            case NON_TRADING -> new RetryPolicy(
                    DhanProtocolConstants.RETRY_COUNT_NON_TRADING,
                    DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                    DhanProtocolConstants.RETRY_MAX_DELAY_MS,
                    DhanProtocolConstants.RETRY_FAILURE_THRESHOLD,
                    DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS);
        };
    }

    private static void sleepBackoff(int attempt, RetryPolicy policy) {
        long delayMs = BackoffStrategy.computeDelayMs(attempt, policy.baseDelayMs(), policy.maxDelayMs());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Dhan operation", ex);
        }
    }
}
