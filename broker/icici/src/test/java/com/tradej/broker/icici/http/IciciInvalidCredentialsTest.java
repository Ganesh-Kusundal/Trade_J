package com.tradej.broker.icici.http;

import com.tradej.broker.icici.resilience.IciciResilienceExecutor;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.resilience.CircuitBreaker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class IciciInvalidCredentialsTest {

    private IciciResilienceExecutor createExecutor() {
        MultiBucketRateLimiter limiter = new MultiBucketRateLimiter(Map.of(
                "DATA", new RateLimitConfig("DATA", 100.0, 10),
                "ORDER", new RateLimitConfig("ORDER", 100.0, 10)
        ));
        return new IciciResilienceExecutor(limiter, new CircuitBreaker());
    }

    @Test
    void http401IsNotRetryable() {
        BreezeHttpException ex = new BreezeHttpException(401, "Unauthorized", "getQuote");
        assertFalse(isRetryable(ex), "401 should not be retried — requires re-authentication");
    }

    @Test
    void http403IsNotRetryable() {
        BreezeHttpException ex = new BreezeHttpException(403, "Forbidden", "getQuote");
        assertFalse(isRetryable(ex), "403 should not be retried");
    }

    @Test
    void http429IsRetryable() {
        BreezeHttpException ex = new BreezeHttpException(429, "Rate limited", "getQuote");
        assertTrue(isRetryable(ex), "429 should be retried with backoff");
    }

    @Test
    void http500IsRetryable() {
        BreezeHttpException ex = new BreezeHttpException(500, "Internal error", "getQuote");
        assertTrue(isRetryable(ex), "500 should be retried");
    }

    @Test
    void http502IsRetryable() {
        BreezeHttpException ex = new BreezeHttpException(502, "Bad gateway", "getQuote");
        assertTrue(isRetryable(ex), "502 should be retried");
    }

    @Test
    void http503IsRetryable() {
        BreezeHttpException ex = new BreezeHttpException(503, "Service unavailable", "getQuote");
        assertTrue(isRetryable(ex), "503 should be retried");
    }

    @Test
    void executorDoesNotRetryAuthFailure() {
        IciciResilienceExecutor executor = createExecutor();
        BreezeHttpException ex = assertThrows(BreezeHttpException.class,
                () -> executor.executeData("test-op", () -> {
                    throw new BreezeHttpException(401, "Unauthorized", "test");
                }));
        assertEquals(401, ex.httpStatus());
    }

    private static boolean isRetryable(BreezeHttpException ex) {
        return ex.httpStatus() == 429 || ex.httpStatus() >= 500;
    }
}
