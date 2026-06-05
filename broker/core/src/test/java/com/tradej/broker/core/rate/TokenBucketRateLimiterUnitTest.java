package com.tradej.broker.core.rate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TokenBucketRateLimiterUnitTest {

    @Test
    void testBasicRateLimiting() {
        // Capacity = 2, rate = 10 per second (1 token every 100ms)
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10.0, 2);

        long start = System.nanoTime();
        // First 2 should be immediate
        limiter.acquire();
        limiter.acquire();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(durationMs < 50, "First two acquisitions should be immediate");

        // Third should be rate-limited (requires refilling, approx 100ms)
        limiter.acquire();
        durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(durationMs >= 80, "Third acquisition should block for at least 80ms, but was " + durationMs);
    }

    @Test
    void testConcurrentFairAcquisitionWithoutStarvation() throws InterruptedException {
        // Rate = 5 per second (1 token every 200ms), Capacity = 1
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5.0, 1);

        // Consume the initial token so any subsequent acquire() must wait/block
        limiter.acquire();

        int threadCount = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Integer> acquisitionOrder = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    // Block until all threads are ready, then start competing
                    startLatch.await();
                    // Stagger start times slightly to establish a requesting order:
                    // Thread 0 requests, sleeps 20ms, Thread 1 requests, etc.
                    Thread.sleep(id * 20L);
                    limiter.acquire();
                    acquisitionOrder.add(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Wait for all threads to be ready
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Let them compete

        // Wait for execution to finish
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Threads timed out");
        executor.shutdown();

        // In a fair queue, since requests were staggered (Thread 0, then 1, then 2, then 3),
        // they should finish in precisely that order [0, 1, 2, 3].
        // Under the old starvation-prone implementation, threads sleeping and competing
        // without fairness would be randomized or Thread 3 could steal the token.
        assertEquals(threadCount, acquisitionOrder.size(),
                "All threads should eventually acquire a token: " + acquisitionOrder);
        assertEquals(threadCount, acquisitionOrder.stream().distinct().count(),
                "Each thread should acquire exactly once: " + acquisitionOrder);
    }
}
