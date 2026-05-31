package com.tradej.broker.core.rate;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-category token-bucket rate limiter.
 * <p>
 * Each API category ({@code ORDER}, {@code DATA}, {@code QUOTE}, etc.)
 * gets its own independent token bucket. Acquiring a token for one category
 * never affects the other categories.
 * <p>
 * Thread-safe (delegates to {@link TokenBucketRateLimiter#acquire()} which is synchronized).
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
}
