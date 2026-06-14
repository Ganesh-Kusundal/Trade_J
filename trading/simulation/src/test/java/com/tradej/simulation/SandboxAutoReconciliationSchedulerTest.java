package com.tradej.simulation;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.service.PositionService;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SandboxAutoReconciliationSchedulerTest {

    @Test
    void detectsDriftBetweenCanonicalAndBroker() {
        PositionService svc = new PositionService();
        // Internal state: long 10 SBIN
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        // Broker view: long 8 SBIN (2 shares missed fill on broker side)
        SandboxAutoReconciliationScheduler.BrokerPositionView broker =
                () -> Map.of("SBIN", 8L);

        SandboxAutoReconciliationScheduler scheduler =
                new SandboxAutoReconciliationScheduler(svc, broker);
        int drift = scheduler.reconcileOnce(false);

        assertEquals(1, drift, "Drift of 2 shares (canonical=10, broker=8) should be detected");
        assertEquals(10L, svc.getNetPosition("SBIN"), "Dry-run must not modify canonical state");
    }

    @Test
    void appliesBrokerViewOnCorrection() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        SandboxAutoReconciliationScheduler.BrokerPositionView broker =
                () -> Map.of("SBIN", 7L);

        SandboxAutoReconciliationScheduler scheduler =
                new SandboxAutoReconciliationScheduler(svc, broker);
        int drift = scheduler.reconcileOnce(true);

        assertEquals(1, drift);
        assertEquals(7L, svc.getNetPosition("SBIN"), "applyBrokerSnapshot must overwrite canonical state");
    }

    @Test
    void noDriftWhenViewsMatch() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        SandboxAutoReconciliationScheduler.BrokerPositionView broker =
                () -> Map.of("SBIN", 10L);

        SandboxAutoReconciliationScheduler scheduler =
                new SandboxAutoReconciliationScheduler(svc, broker);
        int drift = scheduler.reconcileOnce(false);

        assertEquals(0, drift);
        assertEquals(10L, svc.getNetPosition("SBIN"));
    }

    @Test
    void driftOnBrokerSideOnly() {
        // Canonical has nothing, broker reports a position (e.g., a trade that
        // happened on broker side but not yet propagated to canonical)
        PositionService svc = new PositionService();

        SandboxAutoReconciliationScheduler.BrokerPositionView broker =
                () -> Map.of("SBIN", 5L);

        SandboxAutoReconciliationScheduler scheduler =
                new SandboxAutoReconciliationScheduler(svc, broker);
        int drift = scheduler.reconcileOnce(true);

        assertEquals(1, drift);
        assertEquals(5L, svc.getNetPosition("SBIN"), "Broker-only positions must be applied");
    }

    @Test
    void toleranceAbsorbsSmallDrift() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        // Drift of 2 shares, tolerance of 5 → no drift
        SandboxAutoReconciliationScheduler.BrokerPositionView broker =
                () -> Map.of("SBIN", 8L);

        SandboxAutoReconciliationScheduler scheduler =
                new SandboxAutoReconciliationScheduler(svc, broker, 5L);
        int drift = scheduler.reconcileOnce(false);

        assertEquals(0, drift, "Drift within tolerance must not be reported");
    }

    private static TradeOpened open(String tradeId, String symbol, Side side,
                                   long size, long entryPricePaisa) {
        return new TradeOpened(
                EventMetadata.root(),
                tradeId,
                "ord-" + tradeId,
                "sig-" + tradeId,
                symbol,
                side,
                size,
                entryPricePaisa,
                entryPricePaisa - 5_000L,
                entryPricePaisa + 10_000L
        );
    }
}
