package com.tradej.broker.dhan.reactive.resilience;

/**
 * Rate limit configuration for a single token bucket.
 *
 * @param category     Category name (e.g., "DATA", "QUOTE", "OPTION_CHAIN")
 * @param ratePerSecond Token bucket fill rate (tokens/second)
 * @param capacity     Maximum tokens the bucket can hold (burst capacity)
 */
public record RateLimitConfig(
    String category,
    double ratePerSecond,
    int capacity
) {
    
    public RateLimitConfig {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category must not be blank");
        }
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("Rate must be positive: " + ratePerSecond);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
    }
}
