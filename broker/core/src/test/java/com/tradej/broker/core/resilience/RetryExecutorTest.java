package com.tradej.broker.core.resilience;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class RetryExecutorTest {

    private static MultiBucketRateLimiter unlimitedLimiter() {
        return new MultiBucketRateLimiter(Map.of(
                "orders", new RateLimitConfig("orders", 10_000.0, 10_000L)));
    }

    @Test
    void retriesRetryableFailuresThenSucceeds() {
        CircuitBreaker breaker = new CircuitBreaker();
        RetryExecutor executor = new RetryExecutor(unlimitedLimiter(), breaker);
        RetryPolicy policy = new RetryPolicy(3, 1L, 5L, 5, 30_000L);
        int[] attempts = {0};

        String result = executor.execute("orders", "place", policy, () -> {
            attempts[0]++;
            if (attempts[0] < 3) {
                throw new RuntimeException("HTTP 429 Too Many Requests");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts[0]);
    }

    @Test
    void fastFailsNonRetryableErrors() {
        CircuitBreaker breaker = new CircuitBreaker();
        RetryExecutor executor = new RetryExecutor(unlimitedLimiter(), breaker);
        RetryPolicy policy = new RetryPolicy(3, 1L, 5L, 5, 30_000L);

        assertThrows(IllegalArgumentException.class, () -> executor.execute("orders", "place", policy, () -> {
            throw new IllegalArgumentException("bad request");
        }));
    }
}
