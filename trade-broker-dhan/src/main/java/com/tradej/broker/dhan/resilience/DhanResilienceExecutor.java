package com.tradej.broker.dhan.resilience;

import com.tradej.broker.dhan.auth.DhanAuthenticationException;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.rate.MultiBucketRateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DhanResilienceExecutor {
    private final MultiBucketRateLimiter rateLimiter;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public DhanResilienceExecutor(MultiBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public <T> T execute(ApiCategory category, String operation, Supplier<T> supplier) {
        CircuitState state = circuits.computeIfAbsent(operation, ignored -> new CircuitState());
        state.assertCanExecute(operation);
        RuntimeException lastFailure = null;
        int attempts = retryAttempts(category);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                rateLimiter.acquire(category);
                T value = supplier.get();
                state.onSuccess();
                return value;
            } catch (DhanAuthenticationException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                state.onFailure();
                if (attempt < attempts) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new DhanHttpException("Dhan operation failed after retries: " + operation, lastFailure);
    }

    public void run(ApiCategory category, String operation, Runnable action) {
        execute(category, operation, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    private static int retryAttempts(ApiCategory category) {
        return switch (category) {
            case ORDER -> DhanProtocolConstants.RETRY_COUNT_ORDER;
            case DATA -> DhanProtocolConstants.RETRY_COUNT_DATA;
            case QUOTE -> DhanProtocolConstants.RETRY_COUNT_QUOTE;
            case OPTION_CHAIN -> DhanProtocolConstants.RETRY_COUNT_OPTION_CHAIN;
            case NON_TRADING -> DhanProtocolConstants.RETRY_COUNT_NON_TRADING;
        };
    }

    private static void sleepBackoff(int attempt) {
        long delayMs = DhanBackoffUtil.computeDelayMs(
                attempt,
                DhanProtocolConstants.RETRY_BASE_DELAY_MS,
                DhanProtocolConstants.RETRY_MAX_DELAY_MS
        );
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Dhan operation", ex);
        }
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private long openUntilEpochMs;

        private synchronized void assertCanExecute(String operation) {
            long now = System.currentTimeMillis();
            if (openUntilEpochMs > now) {
                throw new IllegalStateException("Circuit is open for Dhan operation " + operation + " until " + openUntilEpochMs);
            }
            if (openUntilEpochMs != 0L && now >= openUntilEpochMs) {
                openUntilEpochMs = 0L;
                consecutiveFailures = 0;
            }
        }

        private synchronized void onSuccess() {
            consecutiveFailures = 0;
            openUntilEpochMs = 0L;
        }

        private synchronized void onFailure() {
            consecutiveFailures++;
            if (consecutiveFailures >= DhanProtocolConstants.RETRY_FAILURE_THRESHOLD) {
                openUntilEpochMs = System.currentTimeMillis() + DhanProtocolConstants.RETRY_CIRCUIT_OPEN_MS;
            }
        }
    }
}
