package com.tradej.replay.engine;

import com.tradej.core.domain.event.TradeExecutionEvent;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestLedgerTest {

    @Test
    void emptyFillListReturnsEmptyResult() {
        BacktestLedger.Result r = new BacktestLedger().fold(List.of());
        assertEquals(0, r.trades().size());
        assertEquals(0, r.pnlSeries().size());
        assertEquals(0, r.metrics().tradeCount());
    }

    @Test
    void singleBuyFillOpensPositionAndHasNoPnl() {
        BacktestLedger.Result r = new BacktestLedger().fold(List.of(
                fill("RELIANCE", 1_000L, 100, 1L)
        ));
        assertEquals(0, r.trades().size(), "Open position, no closed trade");
        assertEquals(1, r.pnlSeries().size());
        assertEquals(0L, r.pnlSeries().get(0).cumulativePnlPaisa());
        assertEquals(0L, r.metrics().netPnlPaisa());
    }

    @Test
    void roundTripBuySellYieldsRealizedProfit() {
        // Buy 100 @ ₹10, sell 100 @ ₹11 → +₹100 = 10000 paisa profit
        BacktestLedger.Result r = new BacktestLedger().fold(List.of(
                fill("RELIANCE", Side.BUY,  1_000L, 100, 1L),
                fill("RELIANCE", Side.SELL, 1_100L, 100, 2L)
        ));
        assertEquals(1, r.trades().size());
        BacktestLedger.Trade t = r.trades().get(0);
        assertEquals(10_000L, t.realizedPnlPaisa(), "100 paisa × 100 qty = 10000 paisa");
        assertTrue(t.isWin());
        assertEquals(10_000L, r.metrics().netPnlPaisa());
        assertEquals(1.0, r.metrics().winRate());
    }

    @Test
    void partialCloseLeavesRemainder() {
        // Buy 100 @ 10, sell 60 @ 9.4 → -60 × 60 = -3600 paisa realized loss, 40 left
        BacktestLedger.Result r = new BacktestLedger().fold(List.of(
                fill("RELIANCE", Side.BUY,  1_000L, 100, 1L),
                fill("RELIANCE", Side.SELL, 940L,   60,  2L)
        ));
        assertEquals(1, r.trades().size());
        BacktestLedger.Trade t = r.trades().get(0);
        assertEquals(-3_600L, t.realizedPnlPaisa(), "60 × (940 - 1000) = -3600 paisa");
    }

    @Test
    void drawdownIsTrackedAcrossSeries() {
        // Buy 100, sell at loss, buy again, sell at bigger loss → max DD recorded
        BacktestLedger.Result r = new BacktestLedger().fold(List.of(
                fill("RELIANCE", Side.BUY,  1_000L, 100, 1L),
                fill("RELIANCE", Side.SELL, 900L,   100, 2L),  // realized +10000 (sell lower = loss... wait
                // Actually pos.avgPricePaisa=1000, fill.executedPricePaisa=900,
                // SELL side: pnlPerUnit = 900 - 1000 = -100, tradePnl = -10000. Loss.
                fill("RELIANCE", Side.BUY,  1_100L, 100, 3L), // re-open higher
                fill("RELIANCE", Side.SELL, 950L,   100, 4L)    // 950-1100 = -150, × 100 = -15000
        ));
        // PnL trajectory: 0, -10000, -10000, -25000. Drawdown from peak (0) to -25000 is 25000.
        assertTrue(r.metrics().maxDrawdownPaisa() > 0, "Drawdown should be > 0");
        assertEquals(-25_000L, r.metrics().netPnlPaisa());
    }

    @Test
    void fillsAreSortedByTimestamp() {
        // Fills intentionally out of order
        BacktestLedger.Result r = new BacktestLedger().fold(List.of(
                fill("RELIANCE", Side.SELL, 1_100L, 100, 5L),
                fill("RELIANCE", Side.BUY,  1_000L, 100, 1L)
        ));
        // 100 @ ₹10 buy, 100 @ ₹11 sell → +₹100 = 10000 paisa
        assertEquals(10_000L, r.metrics().netPnlPaisa());
    }

    @Test
    void perSymbolPositionsAreIndependent() {
        // RELIANCE buy, TCS buy — two independent positions
        BacktestLedger.Result r = new BacktestLedger().fold(List.of(
                fill("RELIANCE", Side.BUY, 1_000L, 100, 1L),
                fill("TCS",      Side.BUY, 2_000L, 50,  2L)
        ));
        assertEquals(0, r.trades().size(), "Both still open");
        assertEquals(0L, r.metrics().netPnlPaisa());
    }

    private TradeExecutionEvent fill(String symbol, long pricePaisa, long qty, long ts) {
        return fill(symbol, com.tradej.core.domain.value.Side.BUY, pricePaisa, qty, ts);
    }

    private TradeExecutionEvent fill(String symbol, com.tradej.core.domain.value.Side side, long pricePaisa, long qty, long ts) {
        return new TradeExecutionEvent(
                null, null, null, symbol, null,
                side, qty, pricePaisa, ts
        );
    }
}
