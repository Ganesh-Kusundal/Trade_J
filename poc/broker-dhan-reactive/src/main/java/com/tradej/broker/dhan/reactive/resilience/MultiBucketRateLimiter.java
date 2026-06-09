package com.tradej.broker.dhan.reactive.resilience;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-category token-bucket rate limiter.
 * <p>
 * Each API category (DATA, QUOTE, OPTION_CHAIN, etc.)
 * gets its own independent token bucket. Acquiring a token for one category
 * never affects the other categories.
 * <p>
 * Thread-safe (delegates to TokenBucketRateLimiter.acquire() which is synchronized).
 */
public final class MultiBucketRateLimiter {

    private final Map<String, TokenBucketRateLimiter> buckets;

    public MultiBucketRateLimiter(Map<String, RateLimitConfig> configs) {
        Map<String, TokenBucketRateLimiter> map = new HashMap<>();
        for (var entry : configs.entrySet()) {
            String name = entry.getKey();
            RateLimitConfig cfg = entry.getValue();
            map.put(name, new TokenBucketRateLimiter(cfg.ratePerSecond(), cfg.capacity()));
        }
        this.buckets = Map.copyOf(map);
    }

    /**
     * Acquires a rate limit token for the given category.
     * Blocks until a token is available.
     *
     * @param categoryName the category to acquire a token for
     * @throws IllegalArgumentException if category is null
     */
    public void acquire(String categoryName) {
        if (categoryName == null) {
            throw new IllegalArgumentException(
                    "Rate limit category must not be null — rate limiting cannot be bypassed");
        }
        TokenBucketRateLimiter limiter = buckets.get(categoryName);
        if (limiter != null) {
            limiter.acquire();
        }
    }

    /**
     * Try to acquire a token without blocking.
     *
     * @return true if token acquired, false if bucket empty
     */
    public boolean tryAcquire(String categoryName) {
        if (categoryName == null) {
            throw new IllegalArgumentException("Category must not be null");
        }
        TokenBucketRateLimiter limiter = buckets.get(categoryName);
        if (limiter != null) {
            return limiter.tryAcquire();
        }
        return true; // No limiter for this category
    }

    /**
     * Get available tokens for a category (for monitoring).
     */
    public double getAvailableTokens(String categoryName) {
        TokenBucketRateLimiter limiter = buckets.get(categoryName);
        if (limiter == null) {
            return 0;
        }
        return limiter.getAvailableTokens();
    }

    /**
     * Get all configured categories.
     */
    public Map<String, TokenBucketRateLimiter> getBuckets() {
        return buckets;
    }
}
