package com.tradej.broker.dhan.reactive.load;

import com.tradej.broker.dhan.reactive.batch.HistoricalBatchProcessor;
import com.tradej.broker.dhan.reactive.batch.HistoricalBatchProcessor.DateRange;
import com.tradej.broker.dhan.reactive.resilience.DhanRateLimits;
import com.tradej.broker.dhan.reactive.resilience.MultiBucketRateLimiter;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for rate limiting, historical batching, and WebSocket scalability.
 * Validates production readiness under maximum load conditions.
 */
class LoadCapacityTest {
    
    @Nested
    class RateLimiterLoadTests {
        
        @Test
        void rateLimiter_50Requests_SerializesAt5PerSecond() throws InterruptedException {
            MultiBucketRateLimiter rateLimiter = DhanRateLimits.createDefault();
            
            int requestCount = 50;
            CountDownLatch latch = new CountDownLatch(requestCount);
            AtomicInteger completedCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            ExecutorService executor = Executors.newFixedThreadPool(10);
            
            for (int i = 0; i < requestCount; i++) {
                executor.submit(() -> {
                    try {
                        rateLimiter.acquire("DATA");
                        completedCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            assertEquals(requestCount, completedCount.get(), "All requests should complete");
            // 50 requests at 5/sec = ~10 seconds minimum
            assertTrue(elapsed >= 9000, 
                "Should take at least 9 seconds for 50 requests at 5/sec, took: " + elapsed + "ms");
        }
        
        @Test
        void multiBucketRateLimiter_IsolatesCategories() {
            MultiBucketRateLimiter rateLimiter = DhanRateLimits.createDefault();
            
            // Each category should have independent limits
            long start = System.currentTimeMillis();
            rateLimiter.acquire("DATA");
            rateLimiter.acquire("QUOTE");
            rateLimiter.acquire("OPTION_CHAIN");
            long elapsed = System.currentTimeMillis() - start;
            
            // Should be fast since each category is independent
            assertTrue(elapsed < 1000, 
                "Different categories should not block each other, took: " + elapsed + "ms");
        }
    }
    
    @Nested
    class HistoricalBatchingLoadTests {
        
        @Test
        void historicalBatchProcessor_10YearIntraday_SplitsCorrectly() {
            LocalDate start = LocalDate.now().minusYears(10);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitIntradayRange(start, end);
            
            // 10 years = 3650 days / 90 days per batch = ~41 batches
            assertTrue(batches.size() >= 40, 
                "Should split into at least 40 batches for 10 years, got: " + batches.size());
            
            // Each batch should be <= 90 days
            assertTrue(batches.stream().allMatch(batch -> batch.daysBetween() <= 90),
                "All batches should be <= 90 days");
        }
        
        @Test
        void historicalBatchProcessor_100DayOptions_SplitsCorrectly() {
            LocalDate start = LocalDate.now().minusDays(100);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitOptionsRange(start, end);
            
            // 100 days / 30 days per batch = 4 batches
            assertEquals(4, batches.size(), "Should split into 4 batches");
            
            // Each batch should be <= 30 days
            assertTrue(batches.stream().allMatch(batch -> batch.daysBetween() <= 30),
                "All batches should be <= 30 days");
        }
        
        @Test
        void historicalBatchProcessor_5YearDaily_SplitsCorrectly() {
            LocalDate start = LocalDate.now().minusYears(5);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitDailyRange(start, end);
            
            // 5 years = 1825 days / 3650 days = 1 batch (no split needed)
            assertEquals(1, batches.size(), "Should not split for 5 years of daily data");
        }
    }
    
    @Nested
    class WebSocketScalabilityTests {
        
        @Test
        void webSocketBatching_1000Symbols_CalculatesCorrectBatches() {
            int totalSymbols = 1000;
            int batchSize = DhanRateLimits.WS_MAX_INSTRUMENTS_PER_SUBSCRIPTION; // 100
            
            int expectedBatches = (int) Math.ceil((double) totalSymbols / batchSize);
            
            assertEquals(10, expectedBatches, "Should require 10 batches for 1000 symbols");
        }
        
        @Test
        void webSocketBatching_5000Symbols_MaxPerConnection() {
            int totalSymbols = 5000;
            int maxPerConnection = DhanRateLimits.WS_MAX_INSTRUMENTS_PER_CONNECTION; // 5000
            
            assertTrue(totalSymbols <= maxPerConnection,
                "5000 symbols should fit in single connection (max 5000)");
        }
        
        @Test
        void webSocketBatching_ExceedsMaxPerConnection_RequiresMultipleConnections() {
            int totalSymbols = 6000;
            int maxPerConnection = DhanRateLimits.WS_MAX_INSTRUMENTS_PER_CONNECTION;
            int batchSize = DhanRateLimits.WS_MAX_INSTRUMENTS_PER_SUBSCRIPTION;
            
            int connectionsNeeded = (int) Math.ceil((double) totalSymbols / maxPerConnection);
            int batchesPerConnection = maxPerConnection / batchSize;
            
            assertEquals(2, connectionsNeeded, "Should need 2 connections for 6000 symbols");
            assertEquals(50, batchesPerConnection, "Each connection should handle 50 batches");
        }
    }
    
    @Nested
    class ConcurrentAccessTests {
        
        @Test
        void tokenBucketRateLimiter_ThreadSafe_UnderConcurrentLoad() throws InterruptedException {
            var limiter = new com.tradej.broker.dhan.reactive.resilience.TokenBucketRateLimiter(
                "TEST", 10.0, 10);
            
            int threadCount = 20;
            int requestsPerThread = 10;
            int totalRequests = threadCount * requestsPerThread;
            
            CountDownLatch latch = new CountDownLatch(totalRequests);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                for (int j = 0; j < requestsPerThread; j++) {
                    executor.submit(() -> {
                        try {
                            limiter.acquire();
                            successCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertEquals(totalRequests, successCount.get(), 
                "All requests should complete under concurrent access");
        }
    }
}
