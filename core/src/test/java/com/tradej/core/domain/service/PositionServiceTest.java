package com.tradej.core.domain.service;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PositionServiceTest {

    // ── Open + close lifecycle ─────────────────────────────────────────

    @Test
    void openTradeUpdatesNetPositionAndAveragePrice() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        assertEquals(10L, svc.getNetPosition("SBIN"));
        assertEquals(100_000L, svc.getPosition("SBIN").averagePricePaisa());
    }

    @Test
    void sellTradeProducesNegativeNetPosition() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.SELL, 10L, 100_000L));

        assertEquals(-10L, svc.getNetPosition("SBIN"));
    }

    @Test
    void averagingAddsToExistingPosition() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));
        svc.onDomainEvent(open("t-2", "SBIN", Side.BUY, 10L, 120_000L));

        // (10*100k + 10*120k) / 20 = 110k
        assertEquals(20L, svc.getNetPosition("SBIN"));
        assertEquals(110_000L, svc.getPosition("SBIN").averagePricePaisa());
    }

    @Test
    void closeTradeReducesPositionToZeroAndAccumulatesRealizedPnl() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));
        svc.onDomainEvent(close("t-1", "SBIN", 110_000L, 10_000L, 10L));

        assertEquals(0L, svc.getNetPosition("SBIN"));
        assertEquals(0L, svc.getNetPosition("SBIN")); // idempotent lookup
        assertEquals(10_000L, svc.getRealizedPnlPaisa("SBIN"));
        assertEquals(10_000L, svc.getTotalRealizedPnlPaisa());
    }

    @Test
    void partialCloseKeepsRemainderWithSameAveragePrice() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));
        svc.onDomainEvent(close("t-1", "SBIN", 110_000L, 5_000L, 4L));

        assertEquals(6L, svc.getNetPosition("SBIN"));
        assertEquals(100_000L, svc.getPosition("SBIN").averagePricePaisa()); // avg unchanged
        assertEquals(5_000L, svc.getRealizedPnlPaisa("SBIN"));
    }

    @Test
    void getPositionsReturnsUnmodifiableViewOfNonZeroPositions() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        var positions = svc.getPositions();
        assertEquals(1, positions.size());
        assertEquals(10L, positions.get("SBIN").quantity());
        assertTrue(() -> {
            try {
                positions.put("X", null);
                return false;
            } catch (UnsupportedOperationException expected) {
                return true;
            }
        });
    }

    // ── NetPositionProvider port behaviour ──────────────────────────────

    @Test
    void getNetPositionReturnsZeroForUnknownSymbol() {
        PositionService svc = new PositionService();
        assertEquals(0L, svc.getNetPosition("UNKNOWN"));
    }

    @Test
    void getPositionReturnsZeroPositionForUnknownSymbol() {
        PositionService svc = new PositionService();
        var pos = svc.getPosition("UNKNOWN");
        assertEquals(0L, pos.quantity());
        assertEquals(0L, pos.averagePricePaisa());
    }

    // ── Idempotency and out-of-order ─────────────────────────────────

    @Test
    void reapplyingSameCloseIsIdempotent() {
        PositionService svc = new PositionService();
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));
        TradeClosed closeEvent = close("t-1", "SBIN", 110_000L, 10_000L, 10L);

        svc.onDomainEvent(closeEvent);
        svc.onDomainEvent(closeEvent); // duplicate
        svc.onDomainEvent(closeEvent); // duplicate

        assertEquals(0L, svc.getNetPosition("SBIN"));
        assertEquals(10_000L, svc.getRealizedPnlPaisa("SBIN")); // NOT 30k
    }

    @Test
    void outOfOrderCloseBeforeOpenIsBufferedAndAppliedOnOpen() {
        PositionService svc = new PositionService();

        // Close arrives first (no matching open yet)
        TradeClosed closeFirst = close("t-1", "SBIN", 110_000L, 10_000L, 10L);
        svc.onDomainEvent(closeFirst);
        assertEquals(0L, svc.getNetPosition("SBIN")); // buffered, not applied yet
        assertEquals(0L, svc.getRealizedPnlPaisa("SBIN"));

        // Open arrives — buffered close should drain
        svc.onDomainEvent(open("t-1", "SBIN", Side.BUY, 10L, 100_000L));

        assertEquals(0L, svc.getNetPosition("SBIN"));
        assertEquals(10_000L, svc.getRealizedPnlPaisa("SBIN"));
    }

    // ── Factories ──────────────────────────────────────────────────────

    private static TradeOpened open(String tradeId, String symbol, Side side,
                                   long size, long entryPricePaisa) {
        long stop = side == Side.BUY ? entryPricePaisa - 5_000L : entryPricePaisa + 5_000L;
        long target = side == Side.BUY ? entryPricePaisa + 10_000L : entryPricePaisa - 10_000L;
        return new TradeOpened(
                EventMetadata.root(),
                tradeId,
                "ord-" + tradeId,
                "sig-" + tradeId,
                symbol,
                side,
                size,
                entryPricePaisa,
                stop,
                target
        );
    }

    private static TradeClosed close(String tradeId, String symbol, long exitPricePaisa,
                                     long realizedPnlPaisa, long size) {
        return new TradeClosed(
                EventMetadata.root(),
                tradeId,
                symbol,
                exitPricePaisa,
                realizedPnlPaisa,
                size,
                "test"
        );
    }
}
