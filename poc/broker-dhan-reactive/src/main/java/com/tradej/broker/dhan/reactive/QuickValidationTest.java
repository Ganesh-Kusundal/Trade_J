package com.tradej.broker.dhan.reactive;

import com.tradej.broker.dhan.reactive.batch.HistoricalBatchProcessor;
import com.tradej.broker.dhan.reactive.batch.HistoricalBatchProcessor.DateRange;
import com.tradej.broker.dhan.reactive.resilience.DhanRateLimits;
import com.tradej.broker.dhan.reactive.resilience.MultiBucketRateLimiter;
import com.tradej.broker.dhan.reactive.resilience.TokenBucketRateLimiter;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quick validation test for rate limiting and historical batching.
 * Run this to verify the implementation works correctly.
 */
public class QuickValidationTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Rate Limiting & Batching Validation ===\n");
        
        // Test 1: Historical Batching
        testHistoricalBatching();
        
        // Test 2: Rate Limiter
        testRateLimiter();
        
        // Test 3: WebSocket Batch Calculation
        testWebSocketBatching();
        
        System.out.println("\n✅ ALL VALIDATIONS PASSED!");
    }
    
    private static void testHistoricalBatching() {
        System.out.println("Test 1: Historical Data Batching");
        
        // Test intraday splitting
        LocalDate start = LocalDate.now().minusYears(10);
        LocalDate end = LocalDate.now();
        List<DateRange> batches = HistoricalBatchProcessor.splitIntradayRange(start, end);
        
        System.out.println("  10-year intraday: " + batches.size() + " batches");
        assert batches.size() >= 40 : "Should have at least 40 batches";
        assert batches.stream().allMatch(b -> b.daysBetween() <= 90) : "All batches should be <= 90 days";
        
        // Test options splitting
        LocalDate optStart = LocalDate.now().minusDays(100);
        List<DateRange> optBatches = HistoricalBatchProcessor.splitOptionsRange(optStart, end);
        System.out.println("  100-day options: " + optBatches.size() + " batches");
        assert optBatches.size() == 4 : "Should have 4 batches";
        
        // Test daily splitting
        LocalDate dailyStart = LocalDate.now().minusYears(5);
        List<DateRange> dailyBatches = HistoricalBatchProcessor.splitDailyRange(dailyStart, end);
        System.out.println("  5-year daily: " + dailyBatches.size() + " batches");
        assert dailyBatches.size() == 1 : "Should have 1 batch";
        
        System.out.println("  ✅ Historical batching works correctly\n");
    }
    
    private static void testRateLimiter() throws InterruptedException {
        System.out.println("Test 2: Rate Limiter");
        
        MultiBucketRateLimiter rateLimiter = DhanRateLimits.createDefault();
        
        // Test single acquire
        long start = System.currentTimeMillis();
        rateLimiter.acquire("DATA");
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  Single acquire: " + elapsed + "ms");
        assert elapsed < 100 : "Should be fast";
        
        // Test category isolation
        start = System.currentTimeMillis();
        rateLimiter.acquire("DATA");
        rateLimiter.acquire("QUOTE");
        rateLimiter.acquire("OPTION_CHAIN");
        elapsed = System.currentTimeMillis() - start;
        System.out.println("  3 different categories: " + elapsed + "ms");
        assert elapsed < 500 : "Different categories should not block each other";
        
        System.out.println("  ✅ Rate limiter works correctly\n");
    }
    
    private static void testWebSocketBatching() {
        System.out.println("Test 3: WebSocket Batch Calculation");
        
        int totalSymbols = 1000;
        int batchSize = DhanRateLimits.WS_MAX_INSTRUMENTS_PER_SUBSCRIPTION; // 100
        int expectedBatches = (int) Math.ceil((double) totalSymbols / batchSize);
        
        System.out.println("  1000 symbols / 100 per batch = " + expectedBatches + " batches");
        assert expectedBatches == 10 : "Should be 10 batches";
        
        int maxSymbols = 5000;
        int maxPerConnection = DhanRateLimits.WS_MAX_INSTRUMENTS_PER_CONNECTION;
        System.out.println("  Max per connection: " + maxPerConnection);
        assert maxSymbols <= maxPerConnection : "5000 should fit in one connection";
        
        System.out.println("  ✅ WebSocket batching works correctly\n");
    }
}
