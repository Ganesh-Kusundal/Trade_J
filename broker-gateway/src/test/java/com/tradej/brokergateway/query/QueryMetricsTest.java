package com.tradej.brokergateway.query;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class QueryMetricsTest {

    @Test
    void initialStateIsZero() {
        var metrics = new QueryMetrics();
        assertEquals(0, metrics.queriesExecuted());
        assertEquals(0, metrics.queriesFailed());
        assertEquals(0, metrics.rowsReturned());
        assertEquals(0.0, metrics.cacheHitRatio());
    }

    @Test
    void recordQueryAccumulatesCorrectly() {
        var metrics = new QueryMetrics();
        metrics.recordQuery(50, 100);
        metrics.recordQuery(100, 200);

        assertEquals(2, metrics.queriesExecuted());
        assertEquals(0, metrics.queriesFailed());
        assertEquals(300, metrics.rowsReturned());
        assertEquals(150, metrics.totalLatencyMs());
        assertEquals(100, metrics.maxLatencyMs());
        assertEquals(75, metrics.averageLatencyMs());
    }

    @Test
    void recordFailureIncrementsCounter() {
        var metrics = new QueryMetrics();
        metrics.recordQuery(50, 10);
        metrics.recordFailure();
        assertEquals(2, metrics.queriesExecuted());
        assertEquals(1, metrics.queriesFailed());
    }

    @Test
    void cacheHitRatioCalculatesCorrectly() {
        var metrics = new QueryMetrics();
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();
        assertEquals(0.75, metrics.cacheHitRatio(), 0.001);
    }

    @Test
    void resetClearsAllCounters() {
        var metrics = new QueryMetrics();
        metrics.recordQuery(50, 100);
        metrics.recordCacheHit();
        metrics.reset();
        assertEquals(0, metrics.queriesExecuted());
        assertEquals(0, metrics.rowsReturned());
        assertEquals(0, metrics.cacheHits());
    }
}
