package com.tradej.hotpath.rate;

/**
 * Non-blocking token bucket rate limiter for the hot-path event pipelines.
 *
 * <p>Returns {@code true} from {@link #tryConsume()} if a token is available
 * and {@code false} if the rate has been exceeded — never blocks or sleeps.
 * Events that cannot acquire a token are silently dropped (shed) to protect
 * downstream processing capacity.
 *
 * <p>Thread-safe: all state mutations happen within a {@code synchronized}
 * block that covers only a few arithmetic operations and a nanoTime read,
 * so contention is negligible on the hot path.
 *
 * <p>Usage:
 * <pre>{@code
 * TokenBucket bucket = new TokenBucket(100.0, 50);  // max 100/s, burst 50
 * if (bucket.tryConsume()) {
 *     process(event);
 * } else {
 *     log.warn("Rate limited, dropping event");
 * }
 * }</pre>
 */
public final class TokenBucket {

    private final double maxRatePerSecond;
    private final double burstCapacity;

    private volatile double tokens;
    private volatile long lastRefillNanos;

    /**
     * @param maxRatePerSecond maximum sustained throughput (must be positive)
     * @param burstCapacity    maximum accumulated tokens for burst absorption (must be positive)
     */
    public TokenBucket(double maxRatePerSecond, long burstCapacity) {
        if (maxRatePerSecond <= 0) {
            throw new IllegalArgumentException("maxRatePerSecond must be positive, got " + maxRatePerSecond);
        }
        if (burstCapacity <= 0) {
            throw new IllegalArgumentException("burstCapacity must be positive, got " + burstCapacity);
        }
        this.maxRatePerSecond = maxRatePerSecond;
        this.burstCapacity = burstCapacity;
        this.tokens = burstCapacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Try to consume a single token.
     *
     * @return {@code true} if the token was consumed (within rate limit),
     *         {@code false} if the rate has been exceeded (event should be dropped)
     */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Returns the configured maximum rate (tokens/second).
     */
    public double maxRatePerSecond() {
        return maxRatePerSecond;
    }

    /**
     * Returns the configured burst capacity (maximum accumulated tokens).
     */
    public long burstCapacity() {
        return (long) burstCapacity;
    }

    /**
     * Returns the approximate number of tokens currently available (may be slightly
     * stale if called from a thread that didn't just call {@link #tryConsume()}).
     */
    public synchronized double availableTokens() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = System.nanoTime();
        long previous = lastRefillNanos;
        double elapsedSec = (now - previous) / 1_000_000_000.0;
        if (elapsedSec > 0) {
            lastRefillNanos = now;
            tokens = Math.min(burstCapacity, tokens + (elapsedSec * maxRatePerSecond));
        }
    }
}
