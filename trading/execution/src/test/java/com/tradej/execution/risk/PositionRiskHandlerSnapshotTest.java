package com.tradej.execution.risk;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Track E2 regression: snapshot() must return the CURRENT state on every
 * call, not a cached value from the first call. Prior to the fix, the
 * handler held a {@code volatile StateSnapshot snapshot} field and
 * lazy-initialised it on the first call, returning the same instance for
 * every subsequent call until {@code restore()} cleared the cache. That
 * broke replay isolation: a replay that called {@code snapshot()} twice
 * between two {@code restore()} calls would see the FIRST call's state
 * on the second call. Sibling classes ({@code OrderManagementService},
 * {@code PortfolioEngine}, {@code ReadModelStore}) all build a fresh
 * snapshot every call.
 */
@Tag("unit")
class PositionRiskHandlerSnapshotTest {

    private static NetPositionProvider emptyPositions() {
        return Map::of;
    }

    @Test
    void snapshot_returnsCurrentStateOnEachCall() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                emptyPositions()
        );

        // First event: open a trade. openTrades goes 0 -> 1.
        handler.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "t-1",
                "ord-1",
                "sig-1",
                "SBIN",
                Side.BUY,
                10L,
                75_000L,
                74_000L,
                77_000L
        ), e -> {});

        PositionRiskHandler.StateSnapshot s1 = handler.snapshot();
        assertEquals(1, s1.openTrades(), "snapshot 1 must reflect the open trade");
        assertEquals(0L, s1.realizedLossPaisa(), "no loss yet");
        assertEquals(0, s1.consecutiveLosses(), "no consecutive losses yet");

        // Second event: close the trade with a loss. openTrades goes 1 -> 0,
        // realizedLossPaisa goes 0 -> 500, consecutiveLosses goes 0 -> 1.
        handler.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "t-1",
                "SBIN",
                75_000L,
                -500L, 10L,
                "stop_loss"
        ), e -> {});

        PositionRiskHandler.StateSnapshot s2 = handler.snapshot();
        assertEquals(0, s2.openTrades(), "snapshot 2 must reflect the close");
        assertEquals(500L, s2.realizedLossPaisa(), "snapshot 2 must reflect the realized loss");
        assertEquals(1, s2.consecutiveLosses(), "snapshot 2 must reflect the consecutive loss");

        // Third call (no intervening events) must return the SAME state as s2.
        // This is the key check — the old lazy cache would have returned s1.
        PositionRiskHandler.StateSnapshot s3 = handler.snapshot();
        assertEquals(0, s3.openTrades(), "snapshot 3 must match snapshot 2 (current state)");
        assertEquals(500L, s3.realizedLossPaisa(), "snapshot 3 must match snapshot 2 (current state)");
        assertEquals(1, s3.consecutiveLosses(), "snapshot 3 must match snapshot 2 (current state)");

        // Sanity: the snapshots must be distinct instances (no caching).
        // Using assertNotSame to catch a regression that re-introduces
        // the volatile field cache.
        assertNotSame(s1, s2, "snapshot must build a fresh instance each call (no cache)");
        assertNotSame(s2, s3, "snapshot must build a fresh instance each call (no cache)");
    }
}
