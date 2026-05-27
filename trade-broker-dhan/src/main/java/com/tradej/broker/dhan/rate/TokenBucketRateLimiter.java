package com.tradej.broker.dhan.rate;

import java.util.concurrent.atomic.AtomicLong;

public final class TokenBucketRateLimiter {
    private final double ratePerSecond;
    private final long capacity;
    private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());
    private volatile double tokens;

    public TokenBucketRateLimiter(double ratePerSecond, long capacity) {
        this.ratePerSecond = ratePerSecond;
        this.capacity = Math.max(capacity, 1);
        this.tokens = this.capacity;
    }

    public synchronized void acquire() {
        refill();
        while (tokens < 1.0d) {
            long sleepNanos = (long) ((1.0d - tokens) / ratePerSecond * 1_000_000_000L);
            try {
                long millis = Math.max(1L, sleepNanos / 1_000_000L);
                Thread.sleep(millis);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Dhan rate limit token", interruptedException);
            }
            refill();
        }
        tokens -= 1.0d;
    }

    private void refill() {
        long now = System.nanoTime();
        long previous = lastRefillNanos.getAndSet(now);
        double elapsedSeconds = (now - previous) / 1_000_000_000.0d;
        tokens = Math.min(capacity, tokens + (elapsedSeconds * ratePerSecond));
    }
}
