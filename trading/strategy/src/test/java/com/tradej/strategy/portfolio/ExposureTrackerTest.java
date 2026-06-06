package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ExposureTrackerTest {

    private static final long MAX_EXPOSURE = 500_000_000L;
    private DefaultExposureTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new DefaultExposureTracker(MAX_EXPOSURE);
    }

    @Test
    void checkAndReserveApprovesWithinLimit() {
        String result = tracker.checkAndReserve("SBIN", 100, 1_000_00);
        assertNull(result);
        assertEquals(100, tracker.netPosition("SBIN"));
    }

    @Test
    void checkAndReserveRejectsExcessiveExposure() {
        tracker.checkAndReserve("SBIN", 2000, 1_000_00);
        String result = tracker.checkAndReserve("SBIN", 3500, 1_000_00);
        assertNotNull(result);
        assertTrue(result.contains("net exposure limit exceeded"));
    }

    @Test
    void applySignalDeltaUpdatesPosition() {
        tracker.applySignalDelta("SBIN", 100);
        assertEquals(100, tracker.netPosition("SBIN"));
    }

    @Test
    void reverseSignalDeltaReversesPosition() {
        tracker.applySignalDelta("SBIN", 100);
        tracker.reverseSignalDelta("SBIN", 50);
        assertEquals(50, tracker.netPosition("SBIN"));
    }

    @Test
    void storeAndRemoveSignalDelta() {
        tracker.storeSignalDelta("sig-1", 100L);
        assertEquals(100L, tracker.removeSignalDelta("sig-1"));
        assertNull(tracker.removeSignalDelta("sig-1"));
    }

    @Test
    void onTradeOpenedAdjustsPosition() {
        tracker.applySignalDelta("SBIN", 100);
        TradeOpened trade = new TradeOpened(
                EventMetadata.root(), "trade-1", "ORD-1", "sig-1",
                "SBIN", Side.LONG, 80, 1_000_00, 0L, 0L
        );
        tracker.onTradeOpened(trade, 80, 100);
        assertEquals(80, tracker.netPosition("SBIN"));
    }

    @Test
    void onTradeClosedAdjustsPosition() {
        tracker.applySignalDelta("SBIN", 100);
        assertEquals(100, tracker.netPosition("SBIN"));

        TradeClosed closed = new TradeClosed(
                EventMetadata.root(), "trade-1", "SBIN", 1_100_00, 10_000_00, 10L, "take-profit"
        );
        tracker.onTradeClosed(closed, "SBIN", 100);
        assertEquals(0, tracker.netPosition("SBIN"));
    }

    @Test
    void netPositionReturnsZeroForUnknownSymbol() {
        assertEquals(0L, tracker.netPosition("UNKNOWN"));
    }

    @Test
    void resetClearsAllState() {
        tracker.checkAndReserve("SBIN", 100, 1_000_00);
        tracker.storeSignalDelta("sig-1", 100L);
        tracker.reset();

        assertEquals(0, tracker.netPosition("SBIN"));
        assertNull(tracker.removeSignalDelta("sig-1"));
        assertTrue(tracker.netPositionsSnapshot().isEmpty());
    }

    @Test
    void netPositionsSnapshotIsUnmodifiable() {
        tracker.checkAndReserve("SBIN", 100, 1_000_00);
        assertThrows(UnsupportedOperationException.class, () ->
                tracker.netPositionsSnapshot().put("X", 1L));
    }

    @Test
    void shortPositionIsNegative() {
        tracker.checkAndReserve("SBIN", -100, 1_000_00);
        assertEquals(-100, tracker.netPosition("SBIN"));
    }

    @Test
    void multipleSymbolsTrackedIndependently() {
        tracker.checkAndReserve("SBIN", 100, 1_000_00);
        tracker.checkAndReserve("RELIANCE", 50, 2_000_00);

        assertEquals(100, tracker.netPosition("SBIN"));
        assertEquals(50, tracker.netPosition("RELIANCE"));
    }
}
