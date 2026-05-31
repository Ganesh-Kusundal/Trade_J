package com.tradej.broker.core.rate;

/**
 * Configuration for a single rate-limited category.
 *
 * @param categoryName     unique category identifier (e.g. "ORDER", "DATA")
 * @param ratePerSecond    token-bucket fill rate in tokens/second
 * @param capacity         token-bucket burst capacity
 */
public record RateLimitConfig(
        String categoryName,
        double ratePerSecond,
        long capacity
) {
    public RateLimitConfig {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("ratePerSecond must be > 0");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
    }
}
