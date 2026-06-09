package com.tradej.broker.core.dedup;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MarketTickDedupFilterTest {

    @Test
    void uniqueSequence_passes() {
        MarketTickDedupFilter filter = new MarketTickDedupFilter();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 100));
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 101));
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 102));
    }

    @Test
    void duplicateSequence_dropped() {
        MarketTickDedupFilter filter = new MarketTickDedupFilter();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 100));
        assertTrue(filter.isDuplicate("RELIANCE", "NSE_EQ", 100), "Same sequence should be duplicate");
        assertTrue(filter.isDuplicate("RELIANCE", "NSE_EQ", 99), "Lower sequence should be duplicate");
    }

    @Test
    void differentSymbols_independent() {
        MarketTickDedupFilter filter = new MarketTickDedupFilter();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 100));
        assertFalse(filter.isDuplicate("TCS", "NSE_EQ", 100), "Different symbol should not be duplicate");
        assertFalse(filter.isDuplicate("RELIANCE", "BSE_EQ", 100), "Different segment should not be duplicate");
    }

    @Test
    void reset_clearsState() {
        MarketTickDedupFilter filter = new MarketTickDedupFilter();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 100));
        assertTrue(filter.isDuplicate("RELIANCE", "NSE_EQ", 100));

        filter.reset();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 100), "After reset, same sequence should pass");
    }

    @Test
    void eviction_clearsWhenOverMaxSize() {
        MarketTickDedupFilter filter = new MarketTickDedupFilter(100);
        for (int i = 0; i < 110; i++) {
            filter.isDuplicate("SYM" + i, "NSE_EQ", i);
        }
        // After eviction (triggered at size > 100), filter clears then continues adding
        // So size should be the remaining symbols added after eviction
        assertTrue(filter.size() <= 100, "Filter size should be bounded by maxSize after eviction, was " + filter.size());
    }

    @Test
    void concurrentAccess_noExceptions() throws InterruptedException {
        MarketTickDedupFilter filter = new MarketTickDedupFilter();
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    filter.isDuplicate("SYM" + threadId, "NSE_EQ", j);
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        // No exception means thread-safety is correct
        assertTrue(filter.size() > 0);
    }
}
