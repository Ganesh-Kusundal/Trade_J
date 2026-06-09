package com.tradej.pipeline.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeMetricsTrackerTest {

    @Test
    void testInitialMetrics() {
        NodeMetricsTracker tracker = new NodeMetricsTracker();
        NodeMetrics metrics = tracker.getMetrics();
        assertEquals(0, metrics.processedCount());
        assertEquals(0, metrics.errorCount());
        assertEquals(0, metrics.lastProcessedTimestampMs());
        assertEquals(0, metrics.lastExecutionNs());
        assertEquals(0.0, metrics.averageExecutionNs());
    }

    @Test
    void testRecordSuccess() {
        NodeMetricsTracker tracker = new NodeMetricsTracker();
        long start = System.currentTimeMillis();
        tracker.recordSuccess(1000L);
        long end = System.currentTimeMillis();
        
        NodeMetrics metrics = tracker.getMetrics();
        assertEquals(1, metrics.processedCount());
        assertEquals(0, metrics.errorCount());
        assertEquals(1000L, metrics.lastExecutionNs());
        assertEquals(1000.0, metrics.averageExecutionNs());
        assertTrue(metrics.lastProcessedTimestampMs() >= start && metrics.lastProcessedTimestampMs() <= end);
    }

    @Test
    void testRecordFailure() {
        NodeMetricsTracker tracker = new NodeMetricsTracker();
        tracker.recordFailure(500L);
        
        NodeMetrics metrics = tracker.getMetrics();
        assertEquals(1, metrics.processedCount());
        assertEquals(1, metrics.errorCount());
        assertEquals(500L, metrics.lastExecutionNs());
        assertEquals(500.0, metrics.averageExecutionNs());
    }

    @Test
    void testAverageExecutionNs() {
        NodeMetricsTracker tracker = new NodeMetricsTracker();
        tracker.recordSuccess(100L);
        tracker.recordFailure(200L);
        
        NodeMetrics metrics = tracker.getMetrics();
        assertEquals(2, metrics.processedCount());
        assertEquals(1, metrics.errorCount());
        assertEquals(200L, metrics.lastExecutionNs());
        assertEquals(150.0, metrics.averageExecutionNs());
    }
}
