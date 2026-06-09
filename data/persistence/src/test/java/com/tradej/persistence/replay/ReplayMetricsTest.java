package com.tradej.persistence.replay;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ReplayMetricsTest {

    @Test
    void initialStateIsZero() {
        var metrics = new ReplayMetrics();
        assertEquals(0, metrics.eventsReplayed());
        assertEquals(0, metrics.eventsFailed());
        assertEquals(0, metrics.replayCount());
        assertEquals(1.0, metrics.successRate());
        assertEquals(0, metrics.eventsPerSecond());
    }

    @Test
    void recordReplayAccumulatesCorrectly() {
        var metrics = new ReplayMetrics();
        metrics.recordReplay(100, 5, 2000);
        metrics.recordReplay(200, 10, 3000);

        assertEquals(300, metrics.eventsReplayed());
        assertEquals(15, metrics.eventsFailed());
        assertEquals(2, metrics.replayCount());
        assertEquals(5000, metrics.totalDurationMs());
        assertEquals(3000, metrics.maxDurationMs());
    }

    @Test
    void successRateCalculatesCorrectly() {
        var metrics = new ReplayMetrics();
        metrics.recordReplay(90, 10, 1000);
        assertEquals(0.9, metrics.successRate(), 0.001);
    }

    @Test
    void eventsPerSecondCalculatesCorrectly() {
        var metrics = new ReplayMetrics();
        metrics.recordReplay(1000, 0, 2000);
        assertEquals(500, metrics.eventsPerSecond());
    }

    @Test
    void resetClearsAllCounters() {
        var metrics = new ReplayMetrics();
        metrics.recordReplay(100, 5, 2000);
        metrics.reset();
        assertEquals(0, metrics.eventsReplayed());
        assertEquals(0, metrics.eventsFailed());
        assertEquals(0, metrics.replayCount());
    }

    @Test
    void toStringContainsKeyInfo() {
        var metrics = new ReplayMetrics();
        metrics.recordReplay(100, 5, 2000);
        String str = metrics.toString();
        assertTrue(str.contains("replays=1"));
        assertTrue(str.contains("events=100"));
    }
}
