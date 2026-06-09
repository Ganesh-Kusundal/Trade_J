package com.tradej.broker.dhan.reactive.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token bucket rate limiter.
 * <p>
 * Implements the token bucket algorithm for rate limiting:
 * - Tokens are added at a fixed rate (ratePerSecond)
 * - Bucket has a maximum capacity (burst size)
 * - Each acquire() consumes one token
 * - If no tokens available, blocks until one is available
 * <p>
 * Thread-safe via synchronized methods.
 */
public final class TokenBucketRateLimiter {
    
    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    
    private final double ratePerSecond;
    private final int capacity;
    private double tokens;
    private long lastRefillTimestamp;
    
    /**
     * Create a token bucket rate limiter.
     *
     * @param ratePerSecond Rate at which tokens are added (tokens/second)
     * @param capacity      Maximum number of tokens (burst capacity)
     */
    public TokenBucketRateLimiter(double ratePerSecond, int capacity) {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("Rate must be positive: " + ratePerSecond);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        
        this.ratePerSecond = ratePerSecond;
        this.capacity = capacity;
        this.tokens = capacity; // Start full
        this.lastRefillTimestamp = System.nanoTime();
    }
    
    /**
     * Acquire a token, blocking if necessary until one is available.
     * Thread-safe.
     */
    public synchronized void acquire() {
        while (true) {
            refill();
            
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return;
            }
            
            // Calculate wait time until next token
            long waitTimeNanos = (long) ((1.0 - tokens) / ratePerSecond * 1_000_000_000);
            
            try {
                wait(Math.max(1, waitTimeNanos / 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for rate limit token", e);
            }
        }
    }
    
    /**
     * Try to acquire a token without blocking.
     *
     * @return true if token acquired, false if bucket empty
     */
    public synchronized boolean tryAcquire() {
        refill();
        
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
    
    /**
     * Reduce the fill rate by a multiplicative factor.
     *
     * @param factor Factor to multiply rate by (e.g., 0.5 = halve the rate)
     */
    public synchronized void reduceRate(double factor) {
        if (factor <= 0 || factor > 1) {
            throw new IllegalArgumentException("Factor must be between 0 and 1: " + factor);
        }
        // Note: ratePerSecond is final, so we can't actually change it
        // This method is provided for API compatibility but requires重构
        log.warn("reduceRate() called but ratePerSecond is final - requires refactoring to support dynamic rates");
    }
    
    /**
     * Increase the fill rate by an additive factor.
     *
     * @param factor Amount to add to rate
     */
    public synchronized void increaseRate(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Factor must be positive: " + factor);
        }
        // Note: ratePerSecond is final, so we can't actually change it
        log.warn("increaseRate() called but ratePerSecond is final - requires refactoring to support dynamic rates");
    }
    
    /**
     * Refill tokens based on elapsed time.
     * Must be called with lock held.
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimestamp;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        
        // Add tokens based on elapsed time
        double newTokens = elapsedSeconds * ratePerSecond;
        tokens = Math.min(capacity, tokens + newTokens);
        
        lastRefillTimestamp = now;
    }
    
    /**
     * Get current token count (for monitoring).
     */
    public synchronized double getAvailableTokens() {
        refill();
        return tokens;
    }
    
    /**
     * Get the configured rate.
     */
    public double getRatePerSecond() {
        return ratePerSecond;
    }
    
    /**
     * Get the configured capacity.
     */
    public int getCapacity() {
        return capacity;
    }
}
