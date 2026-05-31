package com.tradej.broker.dhan.rate;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class TokenBucketRateLimiter {
    private final double ratePerSecond;
    private final long capacity;
    private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());
    private final ReentrantLock lock = new ReentrantLock();
    private volatile double tokens;

    public TokenBucketRateLimiter(double ratePerSecond, long capacity) {
        this.ratePerSecond = ratePerSecond;
        this.capacity = Math.max(capacity, 1);
        this.tokens = this.capacity;
    }

    public void acquire() {
        lock.lock();
        try {
            refill();
            while (tokens < 1.0d) {
                long sleepNanos = (long) ((1.0d - tokens) / ratePerSecond * 1_000_000_000L);
                long millis = Math.max(1L, sleepNanos / 1_000_000L);
                // Release lock during sleep to avoid SWL_SLEEP_WITH_LOCK_HELD
                lock.unlock();
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for Dhan rate limit token", interruptedException);
                } finally {
                    lock.lock();
                }
                refill();
            }
            tokens -= 1.0d;
        } finally {
            lock.unlock();
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long previous = lastRefillNanos.getAndSet(now);
        double elapsedSeconds = (now - previous) / 1_000_000_000.0d;
        tokens = Math.min(capacity, tokens + (elapsedSeconds * ratePerSecond));
    }
}
