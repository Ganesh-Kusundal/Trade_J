package com.tradej.execution.position;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link EventSourcedNetPositionProvider} handles duplicate
 * {@link TradeOpened} events correctly — positions must not be double-counted.
 *
 * <p>This is a deployment-blocking certification test per the stabilization program:
 * duplicate WebSocket events from broker retransmission must not corrupt positions.
 */
class EventSourcedNetPositionProviderIdempotencyTest {

    private static final String SYMBOL = "RELIANCE";
    private static final String TRADE_ID = "TRD-001";

    @Test
    void duplicateTradeOpened_doesNotDoubleCount() {
        var provider = new EventSourcedNetPositionProvider();

        // First TradeOpened: buy 100 shares @ 2500
        provider.onDomainEvent(tradeOpened(TRADE_ID, SYMBOL, Side.BUY, 100, 2500_00L));
        assertEquals(100, provider.getNetPosition(SYMBOL), "Position should be 100 after first open");

        // Duplicate TradeOpened with same tradeId: should be IGNORED
        provider.onDomainEvent(tradeOpened(TRADE_ID, SYMBOL, Side.BUY, 100, 2500_00L));
        assertEquals(100, provider.getNetPosition(SYMBOL),
                "Position must remain 100 after duplicate TradeOpened — NO double-count");

        // Duplicate with different quantity (broker correction): should still not double-count
        provider.onDomainEvent(tradeOpened(TRADE_ID, SYMBOL, Side.BUY, 200, 2500_00L));
        assertEquals(100, provider.getNetPosition(SYMBOL),
                "Position must remain 100 — duplicate tradeId must be idempotent regardless of payload");
    }

    @Test
    void multipleDistinctTrades_accumulateCorrectly() {
        var provider = new EventSourcedNetPositionProvider();

        provider.onDomainEvent(tradeOpened("TRD-001", SYMBOL, Side.BUY, 100, 2500_00L));
        provider.onDomainEvent(tradeOpened("TRD-002", SYMBOL, Side.BUY, 50, 2510_00L));

        assertEquals(150, provider.getNetPosition(SYMBOL), "Two distinct trades should accumulate to 150");

        // Duplicate TRD-001 must not add again
        provider.onDomainEvent(tradeOpened("TRD-001", SYMBOL, Side.BUY, 100, 2500_00L));
        assertEquals(150, provider.getNetPosition(SYMBOL), "Duplicate must not increase position");
    }

    @Test
    void oppositeSideTrades_netCorrectly() {
        var provider = new EventSourcedNetPositionProvider();

        provider.onDomainEvent(tradeOpened("TRD-B1", SYMBOL, Side.BUY, 100, 2500_00L));
        provider.onDomainEvent(tradeOpened("TRD-S1", SYMBOL, Side.SELL, 40, 2510_00L));

        assertEquals(60, provider.getNetPosition(SYMBOL), "100 buy - 40 sell = 60 net");

        // Duplicate the sell — must not reduce further
        provider.onDomainEvent(tradeOpened("TRD-S1", SYMBOL, Side.SELL, 40, 2510_00L));
        assertEquals(60, provider.getNetPosition(SYMBOL), "Duplicate sell must not double-reduce position");
    }

    @Test
    void highVolumeDuplicateBurst_handlesGracefully() {
        var provider = new EventSourcedNetPositionProvider();

        // Open one real trade
        provider.onDomainEvent(tradeOpened("TRD-REAL", SYMBOL, Side.BUY, 100, 2500_00L));
        assertEquals(100, provider.getNetPosition(SYMBOL));

        // Burst of 1000 duplicate events for the same tradeId
        for (int i = 0; i < 1000; i++) {
            provider.onDomainEvent(tradeOpened("TRD-REAL", SYMBOL, Side.BUY, 100, 2500_00L));
        }

        assertEquals(100, provider.getNetPosition(SYMBOL),
                "1000 duplicate events must not change position — idempotency must hold under burst");
    }

    @Test
    void outOfOrderTradeClosed_thenTradeOpened_resolvesCorrectly() {
        var provider = new EventSourcedNetPositionProvider();

        // TradeClosed arrives FIRST (out of order) — should be buffered
        var closed = new com.tradej.core.domain.event.TradeClosed(
                EventMetadata.root(),
                "TRD-OOO",
                SYMBOL,
                2500_00L,   // exitPricePaisa
                -500_00L,   // realizedPnlPaisa
                100,        // size
                "TEST"      // reason
        );
        provider.onDomainEvent(closed);
        assertEquals(0, provider.getNetPosition(SYMBOL), "No position yet — close buffered");

        // TradeOpened arrives second — should apply and resolve buffered close
        provider.onDomainEvent(tradeOpened("TRD-OOO", SYMBOL, Side.BUY, 100, 2500_00L));
        assertEquals(0, provider.getNetPosition(SYMBOL),
                "Trade opened then immediately closed by buffered close — position should be 0");

        // Duplicate TradeOpened should NOT re-open the closed position
        provider.onDomainEvent(tradeOpened("TRD-OOO", SYMBOL, Side.BUY, 100, 2500_00L));
        assertEquals(0, provider.getNetPosition(SYMBOL),
                "Duplicate TradeOpened after close must NOT re-open position");
    }

    // ── Helpers ──

    private static TradeOpened tradeOpened(String tradeId, String symbol, Side side, long size, long pricePaisa) {
        return new TradeOpened(
                EventMetadata.root(),
                tradeId,
                "ORD-" + tradeId,    // orderId
                "SIG-" + tradeId,    // signalId
                symbol,
                side,
                size,
                pricePaisa,
                0L,                  // stopLossPaisa
                0L                   // takeProfitPaisa
        );
    }
}
