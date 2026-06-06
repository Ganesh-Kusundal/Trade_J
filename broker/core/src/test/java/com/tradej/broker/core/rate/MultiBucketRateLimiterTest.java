package com.tradej.broker.core.rate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MultiBucketRateLimiterTest {

    private MultiBucketRateLimiter create() {
        return new MultiBucketRateLimiter(Map.of(
                "ORDER", new RateLimitConfig("ORDER", 100.0, 10),
                "DATA", new RateLimitConfig("DATA", 200.0, 20)
        ));
    }

    @Test
    void acquireSucceedsForKnownCategory() {
        MultiBucketRateLimiter limiter = create();
        assertDoesNotThrow(() -> limiter.acquire("ORDER"));
        assertDoesNotThrow(() -> limiter.acquire("DATA"));
    }

    @Test
    void acquireSucceedsForUnknownCategory() {
        MultiBucketRateLimiter limiter = create();
        assertDoesNotThrow(() -> limiter.acquire("UNKNOWN"));
    }

    @Test
    void acquireRejectsNullCategory() {
        MultiBucketRateLimiter limiter = create();
        assertThrows(IllegalArgumentException.class, () -> limiter.acquire(null));
    }

    @Test
    void differentCategoriesAreIndependent() {
        MultiBucketRateLimiter limiter = new MultiBucketRateLimiter(Map.of(
                "SLOW", new RateLimitConfig("SLOW", 1.0, 1),
                "FAST", new RateLimitConfig("FAST", 1000.0, 100)
        ));
        limiter.acquire("SLOW");
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            limiter.acquire("FAST");
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < 500_000_000L, "FAST category should not be blocked by SLOW");
    }

    @Test
    void reduceRateIsNoOpForUnknownCategory() {
        MultiBucketRateLimiter limiter = create();
        assertDoesNotThrow(() -> limiter.reduceRate("NONEXISTENT", 0.5));
    }

    @Test
    void increaseRateIsNoOpForUnknownCategory() {
        MultiBucketRateLimiter limiter = create();
        assertDoesNotThrow(() -> limiter.increaseRate("NONEXISTENT", 0.1));
    }
}
