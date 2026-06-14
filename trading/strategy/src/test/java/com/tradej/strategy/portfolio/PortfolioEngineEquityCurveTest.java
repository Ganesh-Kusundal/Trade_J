package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PortfolioEngineEquityCurveTest {

    @Test
    void equityCurveIsEmptyBeforeAnyClosedTrade() {
        PortfolioEngine engine = new PortfolioEngine();
        assertTrue(engine.equityCurveSnapshot().isEmpty());
    }

    @Test
    void equityCurveAppendsCumulativePnlOnEachClosedTrade() {
        PortfolioEngine engine = new PortfolioEngine();
        // Drive an open → close pair so the engine tracks the trade
        // lifecycle end-to-end. The realised-PnL total must accumulate.
        engine.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "T-1", "O-1", "S-1", "SBIN", Side.BUY,
                10L, 75_000L, 0L, 0L), e -> {});
        engine.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "T-1", "SBIN", 75_500L,
                /* realizedPnlPaisa */ 5_000L,
                10L, "TAKE_PROFIT"), e -> {});

        var snap = engine.equityCurveSnapshot();
        assertEquals(1, snap.size());
        assertEquals(5_000L, snap.get(0).realizedPnlPaisa());
        assertTrue(snap.get(0).timestampMs() > 0L);

        // Second trade: loss of 2 000 → cumulative = 3 000.
        engine.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "T-2", "O-2", "S-2", "RELIANCE", Side.BUY,
                5L, 100_000L, 0L, 0L), e -> {});
        engine.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "T-2", "RELIANCE", 99_600L,
                /* realizedPnlPaisa */ -2_000L,
                5L, "STOP_LOSS"), e -> {});

        snap = engine.equityCurveSnapshot();
        assertEquals(2, snap.size());
        assertEquals(5_000L, snap.get(0).realizedPnlPaisa());
        assertEquals(3_000L, snap.get(1).realizedPnlPaisa());
    }

    @Test
    void equityCurveIsClearedByReset() {
        PortfolioEngine engine = new PortfolioEngine();
        engine.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "T-1", "O-1", "S-1", "SBIN", Side.BUY,
                1L, 1L, 0L, 0L), e -> {});
        engine.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "T-1", "SBIN", 2L, 1_000L, 1L, "TAKE_PROFIT"), e -> {});
        assertNotNull(engine.equityCurveSnapshot());
        assertEquals(1, engine.equityCurveSnapshot().size());

        engine.reset();
        assertTrue(engine.equityCurveSnapshot().isEmpty());
    }

    @Test
    void equityCurveSnapshotIsImmutableToCaller() {
        PortfolioEngine engine = new PortfolioEngine();
        engine.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "T-1", "O-1", "S-1", "SBIN", Side.BUY,
                1L, 1L, 0L, 0L), e -> {});
        engine.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "T-1", "SBIN", 2L, 1L, 1L, "TAKE_PROFIT"), e -> {});

        var snap = engine.equityCurveSnapshot();
        try {
            snap.clear();
            // If we get here the contract is broken; fail loudly.
            assertTrue(false, "snapshot() should be immutable but clear() succeeded");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
