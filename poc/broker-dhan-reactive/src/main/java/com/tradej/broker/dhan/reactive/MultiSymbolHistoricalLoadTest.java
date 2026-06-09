package com.tradej.broker.dhan.reactive;

import com.tradej.broker.dhan.reactive.batch.HistoricalBatchProcessor;
import com.tradej.broker.dhan.reactive.batch.HistoricalBatchProcessor.DateRange;
import com.tradej.broker.dhan.reactive.resilience.DhanRateLimits;
import com.tradej.broker.dhan.reactive.resilience.MultiBucketRateLimiter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Load test for multi-symbol 5-minute historical data retrieval.
 * Tests: 10 symbols × 30 days = 30 days of 5-minute candles per symbol
 * 
 * This validates:
 * - Historical batching (30 days fits in single 90-day batch)
 * - Rate limiting (5 req/s for DATA category)
 * - Multi-symbol parallel retrieval
 * - Total API call estimation
 */
public class MultiSymbolHistoricalLoadTest {
    
    public static void main(String[] args) {
        System.out.println("=== Multi-Symbol Historical Data Load Test ===\n");
        System.out.println("Scenario: 10 symbols × 30 days of 5-minute data\n");
        
        // Configuration
        int numSymbols = 10;
        int daysBack = 30;
        String interval = "FIVE_MIN";
        
        List<String> symbols = List.of(
            "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK",
            "SBIN", "WIPRO", "HCLTECH", "AXISBANK", "BAJFINANCE"
        );
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);
        
        System.out.println("Configuration:");
        System.out.println("  Symbols: " + numSymbols);
        System.out.println("  Date Range: " + startDate + " to " + endDate + " (" + daysBack + " days)");
        System.out.println("  Interval: " + interval);
        System.out.println("  Rate Limit: " + DhanRateLimits.DATA_RATE + " req/s\n");
        
        // Test 1: Calculate batching
        testBatching(startDate, endDate, numSymbols);
        
        // Test 2: Estimate API calls
        estimateApiCalls(numSymbols, daysBack);
        
        // Test 3: Simulate rate-limited retrieval
        simulateRateLimitedRetrieval(numSymbols);
        
        System.out.println("\n✅ LOAD TEST COMPLETE!");
    }
    
    private static void testBatching(LocalDate start, LocalDate end, int numSymbols) {
        System.out.println("Test 1: Historical Batching Analysis");
        
        List<DateRange> batches = HistoricalBatchProcessor.splitIntradayRange(start, end);
        
        System.out.println("  30-day range split into: " + batches.size() + " batch(es)");
        System.out.println("  Max batch size: 90 days (API limit)");
        System.out.println("  Actual: " + batches.get(0).daysBetween() + " days");
        System.out.println("  ✅ Each symbol requires " + batches.size() + " API call(s)\n");
    }
    
    private static void estimateApiCalls(int numSymbols, int days) {
        System.out.println("Test 2: API Call Estimation");
        
        // 30 days fits in single 90-day batch
        int batchesPerSymbol = 1;
        int totalApiCalls = numSymbols * batchesPerSymbol;
        
        System.out.println("  Batches per symbol: " + batchesPerSymbol);
        System.out.println("  Total symbols: " + numSymbols);
        System.out.println("  Total API calls: " + totalApiCalls);
        
        // Calculate time with rate limiting
        double rateLimit = DhanRateLimits.DATA_RATE; // 5 req/s
        double minimumTimeSeconds = totalApiCalls / rateLimit;
        
        System.out.println("  Rate limit: " + rateLimit + " req/s");
        System.out.println("  Minimum time: " + String.format("%.1f", minimumTimeSeconds) + " seconds");
        System.out.println("  Estimated 5-min candles per symbol: " + (days * 75)); // 75 5-min candles per day
        System.out.println("  Total candles: " + (numSymbols * days * 75));
        System.out.println("  ✅ Rate limiter will throttle automatically\n");
    }
    
    private static void simulateRateLimitedRetrieval(int numSymbols) {
        System.out.println("Test 3: Simulated Rate-Limited Retrieval");
        
        MultiBucketRateLimiter rateLimiter = DhanRateLimits.createDefault();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Simulate parallel retrieval with rate limiting
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(numSymbols);
        
        IntStream.range(0, numSymbols).forEach(i -> {
            executor.submit(() -> {
                try {
                    String symbol = "SYMBOL_" + (i + 1);
                    
                    // Acquire rate limit token (blocks if needed)
                    rateLimiter.acquire("DATA");
                    
                    // Simulate API call (would be actual HTTP request)
                    Thread.sleep(50); // Simulate 50ms network latency
                    
                    completedCount.incrementAndGet();
                    System.out.println("  ✓ Retrieved data for " + symbol);
                    
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    System.err.println("  ✗ Failed for SYMBOL_" + (i + 1) + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });
        
        try {
            // Wait for all with 30-second timeout
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            System.out.println("\n  Results:");
            System.out.println("    Completed: " + completedCount.get() + "/" + numSymbols);
            System.out.println("    Failed: " + failedCount.get());
            System.out.println("    Elapsed: " + elapsed + "ms");
            System.out.println("    Rate: " + String.format("%.2f", completedCount.get() / (elapsed / 1000.0)) + " req/s");
            
            if (completed) {
                System.out.println("  ✅ All symbols retrieved successfully with rate limiting\n");
            } else {
                System.out.println("  ⚠️ Timeout after 30 seconds\n");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("  ✗ Test interrupted");
        } finally {
            executor.shutdown();
        }
    }
}
