package com.tradej.hotpath.rate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TokenBucketTest {

    @Test
    void constructionWithPositiveParams() {
        var bucket = new TokenBucket(100.0, 50);
        assertEquals(100.0, bucket.maxRatePerSecond());
        assertEquals(50, bucket.burstCapacity());
    }

    @Test
    void constructionWithZeroRateThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 10));
    }

    @Test
    void constructionWithNegativeRateThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 10));
    }

    @Test
    void constructionWithZeroBurstThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(100, 0));
    }

    @Test
    void constructionWithNegativeBurstThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(100, -1));
    }

    @Test
    void startsFullWithBurstCapacityTokens() {
        var bucket = new TokenBucket(100.0, 50);
        // With burst capacity 50, the first 50 calls should succeed
        for (int i = 0; i < 50; i++) {
            assertTrue(bucket.tryConsume(), "Burst capacity should allow " + (i + 1) + " tokens");
        }
    }

    @Test
    void exhaustsTokensAfterBurst() {
        var bucket = new TokenBucket(100.0, 10);
        for (int i = 0; i < 10; i++) {
            assertTrue(bucket.tryConsume());
        }
        // 11th call should be rate limited
        assertFalse(bucket.tryConsume(), "Should be rate limited after burst is exhausted");
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        // 10 tokens/s, burst 1 — after consuming 1 token, wait >100ms for another
        var bucket = new TokenBucket(10.0, 1);

        assertTrue(bucket.tryConsume());  // consume the single burst token
        assertFalse(bucket.tryConsume(), "Should be rate limited immediately");

        // Wait 150ms — at 10 tokens/s, that's 1.5 tokens, so we should have at least 1
        Thread.sleep(150);

        assertTrue(bucket.tryConsume(), "Should have refilled after waiting");
    }

    @Test
    void doesNotExceedBurstCapacity() throws InterruptedException {
        // 100 tokens/s, burst 5
        var bucket = new TokenBucket(100.0, 5);

        // Wait 200ms — at 100/s, that's 20 tokens, but burst is only 5
        Thread.sleep(200);

        // Should still only have 5 tokens max
        int consumed = 0;
        while (bucket.tryConsume()) {
            consumed++;
        }
        assertEquals(5, consumed, "Should never exceed burst capacity even after long idle");
    }

    @Test
    void highRateAcceptsAllTokens() throws InterruptedException {
        // Very high rate — practically unlimited
        var bucket = new TokenBucket(1_000_000.0, 100);

        // All burst tokens should be available
        for (int i = 0; i < 100; i++) {
            assertTrue(bucket.tryConsume());
        }
    }

    @Test
    void rateLimitingWithVeryLowRate() throws InterruptedException {
        // 1 token per second — burst 1
        var bucket = new TokenBucket(1.0, 1);

        assertTrue(bucket.tryConsume());  // first token
        assertFalse(bucket.tryConsume(), "Should be rate limited");  // second should fail

        Thread.sleep(1_100);  // Wait just over 1 second

        assertTrue(bucket.tryConsume(), "Should have refilled after 1 second");
        assertFalse(bucket.tryConsume(), "Should be rate limited again");
    }

    @Test
    void availableTokensIsNearFullAfterRefill() throws InterruptedException {
        var bucket = new TokenBucket(100.0, 50);
        assertEquals(50.0, bucket.availableTokens(), 1.0);

        // Exhaust
        for (int i = 0; i < 50; i++) {
            bucket.tryConsume();
        }
        assertTrue(bucket.availableTokens() < 1.0);

        // Wait for partial refill
        Thread.sleep(200);

        // After 200ms at 100/s, should have ~20 tokens
        assertTrue(bucket.availableTokens() > 10.0, "Should have refilled ~20 tokens after 200ms");
    }
}
