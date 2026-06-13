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
        // The test name says 'aggregate' — to actually exercise aggregation
        // across two registered sources, BOTH sources must contribute to
        // the same (strategy, eventType, outcome) tuple. The prior version
        // of this test had source `a` with 2 S1|TickEvent|OK events and
        // source `b` with 1 S2|TickEvent|OK event, then asserted the
        // S1|TickEvent|OK aggregate was 3. That asserted a value with
        // no source actually contributing 3 — it was a test bug.
        //
        // The fix: source `b` also contributes to S1|TickEvent|OK so the
        // aggregate (2 from a + 1 from b = 3) matches the assertion.
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        StrategyMetrics a = new StrategyMetrics();
        a.recordOk("S1", "TickEvent");
        a.recordOk("S1", "TickEvent");
        a.recordError("S1", "TickEvent");

        StrategyMetrics b = new StrategyMetrics();
        b.recordOk("S1", "TickEvent");
        b.recordOk("S2", "TickEvent");
        b.recordTimeout("S2", "TickEvent");

        r.register("a", a);
        r.register("b", b);

        var snap = r.snapshot();
        // Aggregate across both sources: a has 2 S1|TickEvent|OK, b has 1 → 3
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
