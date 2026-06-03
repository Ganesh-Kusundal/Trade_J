package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TickReconcilerTest {

    private TickReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new TickReconciler();
    }

    @Test
    void reconcilesPerfectMatch() {
        List<TickReconciler.BrokerTick> brokerTicks = List.of(
                new TickReconciler.BrokerTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                new TickReconciler.BrokerTick(2L, "SBIN", 751_00L, 1010L, 1001000L)
        );

        List<MarketTickEvent> systemTicks = List.of(
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                createSystemTick(2L, "SBIN", 751_00L, 1010L, 1001000L)
        );

        TickReconciler.ReconciliationReport report = reconciler.reconcile("SBIN", brokerTicks, systemTicks);

        assertTrue(report.isReconciled());
        assertEquals(2, report.totalBrokerTicks());
        assertEquals(2, report.totalSystemTicks());
        assertTrue(report.missingTicks().isEmpty());
        assertTrue(report.duplicateTicks().isEmpty());
        assertTrue(report.outOfOrderTicks().isEmpty());
    }

    @Test
    void detectsMissingTicks() {
        List<TickReconciler.BrokerTick> brokerTicks = List.of(
                new TickReconciler.BrokerTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                new TickReconciler.BrokerTick(2L, "SBIN", 751_00L, 1010L, 1001000L)
        );

        List<MarketTickEvent> systemTicks = List.of(
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L)
        );

        TickReconciler.ReconciliationReport report = reconciler.reconcile("SBIN", brokerTicks, systemTicks);

        assertFalse(report.isReconciled());
        assertEquals(2, report.totalBrokerTicks());
        assertEquals(1, report.totalSystemTicks());
        assertEquals(1, report.missingTicks().size());
        assertEquals(2L, report.missingTicks().get(0).sequenceId());
    }

    @Test
    void detectsDuplicateTicks() {
        List<TickReconciler.BrokerTick> brokerTicks = List.of(
                new TickReconciler.BrokerTick(1L, "SBIN", 750_00L, 1000L, 1000000L)
        );

        List<MarketTickEvent> systemTicks = List.of(
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L)
        );

        TickReconciler.ReconciliationReport report = reconciler.reconcile("SBIN", brokerTicks, systemTicks);

        assertFalse(report.isReconciled());
        assertEquals(1, report.totalBrokerTicks());
        assertEquals(2, report.totalSystemTicks());
        assertEquals(1, report.duplicateTicks().size());
    }

    @Test
    void detectsOutOfOrderTicks() {
        List<TickReconciler.BrokerTick> brokerTicks = List.of(
                new TickReconciler.BrokerTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                new TickReconciler.BrokerTick(2L, "SBIN", 751_00L, 1010L, 1001000L)
        );

        List<MarketTickEvent> systemTicks = List.of(
                createSystemTick(2L, "SBIN", 751_00L, 1010L, 1001000L),
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L)
        );

        TickReconciler.ReconciliationReport report = reconciler.reconcile("SBIN", brokerTicks, systemTicks);

        assertFalse(report.isReconciled());
        assertEquals(1, report.outOfOrderTicks().size());
        assertEquals(1L, report.outOfOrderTicks().get(0).sequenceId());
    }

    private MarketTickEvent createSystemTick(long seqId, String symbol, long ltp, long volume, long exchangeTime) {
        return new MarketTickEvent(
                EventMetadata.root(),
                seqId,
                symbol,
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                ltp,
                10L,
                volume,
                exchangeTime,
                Optional.empty(),
                0L,
                0L
        );
    }
}
