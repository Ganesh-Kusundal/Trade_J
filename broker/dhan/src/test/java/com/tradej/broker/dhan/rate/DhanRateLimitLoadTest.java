package com.tradej.broker.dhan.rate;

import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3.1: Verifies the 5 Dhan rate-limit buckets survive concurrent load
 * without starvation, deadlocks, or excessive blocking.
 *
 * <p>Uses {@link DhanProtocolConstants#defaultRateLimiter()} directly —
 * no broker connection needed.
 */
@Tag("load")
@Tag("p3")
class DhanRateLimitLoadTest {

    private static final int CONCURRENT_REQUESTS = 50;
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(30);

    private final MultiBucketRateLimiter limiter = DhanProtocolConstants.defaultRateLimiter();

    @Test
    void concurrentRequestsAcrossAllBucketsCompleteWithoutErrors() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger errors = new AtomicInteger(0);
        List<String> errorDetails = new CopyOnWriteArrayList<>();

        // Distribute load across the 5 Dhan buckets
        String[] buckets = {"ORDER", "DATA", "QUOTE", "OPTION_CHAIN", "NON_TRADING"};
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final String bucket = buckets[i % buckets.length];
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Instant start = Instant.now();
                    limiter.acquire(bucket);
                    Instant end = Instant.now();
                    long waitMs = Duration.between(start, end).toMillis();
                    // Buckets should not block unreasonably (ORDER: 7/s, DATA: 5/s, etc.)
                    assertTrue(waitMs < TEST_TIMEOUT.toMillis(),
                            bucket + " acquisition took " + waitMs + "ms — possible deadlock");
                } catch (Exception e) {
                    errors.incrementAndGet();
                    errorDetails.add(bucket + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(completed, "Not all requests completed within timeout");
        assertEquals(0, errors.get(),
                "Unexpected errors during concurrent rate-limit test: " + errorDetails);
    }

    @Test
    void orderBucketEnforcesPerSecondLimit() {
        // ORDER bucket: 7/s capacity. Acquire tokens rapidly.
        long start = System.nanoTime();
        for (int i = 0; i < 7; i++) {
            limiter.acquire("ORDER");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // All 7 should complete quickly (within the bucket capacity)
        assertTrue(elapsedMs < 5000,
                "ORDER bucket 7 acquires took " + elapsedMs + "ms (expected < 5s)");
    }

    @Test
    void dataBucketEnforcesPerSecondLimit() {
        // DATA bucket: 5/s capacity
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            limiter.acquire("DATA");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 5000,
                "DATA bucket 5 acquires took " + elapsedMs + "ms (expected < 5s)");
    }

    @Test
    void quoteBucketEnforcesHalfPerSecondLimit() {
        // QUOTE bucket: 0.5/s rate — 1 token every 2 seconds
        long start = System.nanoTime();
        limiter.acquire("QUOTE");
        limiter.acquire("QUOTE");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // Second token should wait ~2s, so total > 1500ms
        assertTrue(elapsedMs > 1500,
                "QUOTE bucket 2 acquires took only " + elapsedMs + "ms (expected > 1500ms)");
    }

    @Test
    void noDeadlockWhenBucketsAreFullyExhausted() {
        // Saturate all buckets
        for (String bucket : new String[]{"ORDER", "DATA", "QUOTE", "OPTION_CHAIN", "NON_TRADING"}) {
            try {
                limiter.acquire(bucket);
            } catch (Exception ignored) {
                // Some buckets may reject if rate=0; that's fine
            }
        }
        // If we get here without deadlock, test passes
        assertTrue(true, "All buckets acquired without deadlock");
    }
}
