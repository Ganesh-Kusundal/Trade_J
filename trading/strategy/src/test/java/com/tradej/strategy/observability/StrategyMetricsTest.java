package com.tradej.strategy.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class StrategyMetricsTest {

    @Test
    void countersStartAtZero() {
        StrategyMetrics metrics = new StrategyMetrics();
        assertEquals(0L, metrics.count("A", "T", StrategyMetrics.Outcome.OK));
        assertEquals(0L, metrics.count("A", "T", StrategyMetrics.Outcome.TIMEOUT));
        assertEquals(0L, metrics.count("A", "T", StrategyMetrics.Outcome.ERROR));
    }

    @Test
    void recordOkIncrementsOnlyOkCounter() {
        StrategyMetrics metrics = new StrategyMetrics();
        metrics.recordOk("A", "TickEvent");
        metrics.recordOk("A", "TickEvent");
        metrics.recordOk("A", "TickEvent");
        assertEquals(3L, metrics.count("A", "TickEvent", StrategyMetrics.Outcome.OK));
        assertEquals(0L, metrics.count("A", "TickEvent", StrategyMetrics.Outcome.ERROR));
    }

    @Test
    void recordErrorAndTimeoutIncrementTheirOwnBuckets() {
        StrategyMetrics metrics = new StrategyMetrics();
        metrics.recordError("B", "CandleEvent");
        metrics.recordTimeout("B", "CandleEvent");
        assertEquals(1L, metrics.count("B", "CandleEvent", StrategyMetrics.Outcome.ERROR));
        assertEquals(1L, metrics.count("B", "CandleEvent", StrategyMetrics.Outcome.TIMEOUT));
    }

    @Test
    void differentStrategiesAreIsolated() {
        StrategyMetrics metrics = new StrategyMetrics();
        metrics.recordOk("A", "TickEvent");
        metrics.recordOk("B", "TickEvent");
        assertEquals(1L, metrics.count("A", "TickEvent", StrategyMetrics.Outcome.OK));
        assertEquals(1L, metrics.count("B", "TickEvent", StrategyMetrics.Outcome.OK));
    }

    @Test
    void snapshotReturnsAllCounters() {
        StrategyMetrics metrics = new StrategyMetrics();
        metrics.recordOk("A", "X");
        metrics.recordError("B", "Y");
        var snap = metrics.snapshot();
        assertNotNull(snap);
        assertTrue(snap.containsKey("A|X|OK"));
        assertTrue(snap.containsKey("B|Y|ERROR"));
    }
}
