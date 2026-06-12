package com.tradej.strategy.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@Tag("unit")
class StrategyMetricsRegistryTest {

    @Test
    void emptyRegistryReturnsEmptySnapshot() {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        assertNotNull(r.snapshot());
        assertEquals(0, r.snapshot().size());
    }

    @Test
    void registeredSourceCountersAggregate() {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        StrategyMetrics a = new StrategyMetrics();
        a.recordOk("S1", "TickEvent");
        a.recordOk("S1", "TickEvent");
        a.recordError("S1", "TickEvent");

        StrategyMetrics b = new StrategyMetrics();
        b.recordOk("S2", "TickEvent");
        b.recordTimeout("S2", "TickEvent");

        r.register("a", a);
        r.register("b", b);

        var snap = r.snapshot();
        assertEquals(3, snap.get("S1|TickEvent|OK"));
        assertEquals(1, snap.get("S1|TickEvent|ERROR"));
        assertEquals(1, snap.get("S2|TickEvent|OK"));
        assertEquals(1, snap.get("S2|TickEvent|TIMEOUT"));
    }

    @Test
    void unregisterRemovesSource() {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        StrategyMetrics a = new StrategyMetrics();
        a.recordOk("X", "TickEvent");
        r.register("a", a);
        assertEquals(1, r.snapshot().size());

        r.unregister("a");
        assertEquals(0, r.snapshot().size());
    }

    @Test
    void registeringNullSourceIsNoOp() {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        r.register("x", null); // should not throw
        assertEquals(0, r.snapshot().size());
    }

    @Test
    void sourcesSurviveConcurrentUpdates() throws Exception {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        StrategyMetrics a = new StrategyMetrics();
        r.register("a", a);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1_000; i++) a.recordOk("S", "T");
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1_000; i++) a.recordError("S", "T");
        });
        t1.start(); t2.start();
        t1.join(); t2.join();

        var snap = r.snapshot();
        assertEquals(1_000, snap.get("S|T|OK"));
        assertEquals(1_000, snap.get("S|T|ERROR"));
    }
}
