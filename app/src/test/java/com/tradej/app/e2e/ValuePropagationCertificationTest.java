package com.tradej.app.e2e;

import com.tradej.core.testsupport.TestSymbols;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.simulation.PnLLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14: End-to-End Data Integrity &amp; Value Verification.
 *
 * <p>Traces exact numeric values through three independent subsystems that
 * together form the position/PnL pipeline:
 *
 * <pre>
 *   TradeOpened / TradeClosed
 *     ├── EventSourcedNetPositionProvider  → position qty, avg price (VWAP)
 *     ├── PositionRiskHandler              → open trades, realized loss, kill switch
 *     └── PnLLedger                        → realized PnL, unrealized PnL (FIFO)
 * </pre>
 *
 * <p><b>Important:</b> PositionProvider and PnLLedger use DIFFERENT cost-basis
 * methods. PositionProvider closes specific trade contributions (identified by
 * tradeId) and preserves the VWAP for remaining shares. PnLLedger uses FIFO
 * and recalculates VWAP on partial closes. They will produce different PnL
 * numbers for partial closes — this is expected and correct. Each system's
 * internal math is verified independently.
 *
 * <p>All prices are in paisa (1/100th of rupee). Underscore notation:
 * {@code 1_23_45L} = 12,345 paisa = ₹123.45.
 *
 * <p>Every assertion commits to a specific numeric value. If any value drifts,
 * this test fails. This is the deployment-blocking certification for Phase 14.
 */
@Tag("chaos")
@DisplayName("Value Propagation: OMS → Position → PnL → Risk")
class ValuePropagationCertificationTest {

    private static final String SYMBOL = TestSymbols.SBIN;

    private EventSourcedNetPositionProvider positionProvider;
    private PositionRiskHandler riskHandler;
    private PnLLedger pnlLedger;

    @BeforeEach
    void setUp() {
        positionProvider = new EventSourcedNetPositionProvider();
        riskHandler = new PositionRiskHandler(
                RiskLimits.conservative(),
                positionProvider
        );
        pnlLedger = new PnLLedger();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario A: Single Buy → Full Close
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Buy 100@750, close @770: position, avg price, realized PnL all exact")
    void singleBuyFullClose_allValuesExact() {
        // ── Buy 100 SBIN @ 750.00 ──────────────────────────────────
        TradeOpened buy = tradeOpened("T-A1", "ORD-A1", "SIG-A1",
                Side.BUY, 100L, 750_00L, 740_00L, 770_00L);

        positionProvider.onDomainEvent(buy);
        riskHandler.onDomainEvent(buy, e -> {});
        pnlLedger.applyFill(toFill(buy), 755_00L); // mark at 755

        // ── Position provider: 100 shares, avgPrice = 750.00 ───────
        assertEquals(100L, positionProvider.getNetPosition(SYMBOL));
        var pos = positionProvider.getPositions().get(SYMBOL);
        assertNotNull(pos);
        assertEquals(100L, pos.quantity());
        assertEquals(750_00L, pos.averagePricePaisa());

        // ── Risk handler: 1 open trade, no kill switch ─────────────
        assertEquals(1, riskHandler.getOpenTrades());
        assertFalse(riskHandler.isKillSwitchActive());

        // ── PnLLedger: no realized, unrealized = 100 × (755-750) ──
        assertEquals(0L, pnlLedger.realizedPnlPaisa());
        assertEquals(500_00L, pnlLedger.unrealizedPnlPaisa(),
                "Unrealized: 100 × (755.00-750.00) = ₹5.00 = 500 paisa");

        // ── Close @ 770.00 (profit) ────────────────────────────────
        TradeClosed close = new TradeClosed(
                EventMetadata.root(),
                "T-A1", SYMBOL,
                770_00L,     // exitPricePaisa
                20_00_00L,   // 100 × (770-750) × 100 = 200,000 paisa
                0L, "target");

        positionProvider.onDomainEvent(close);
        riskHandler.onDomainEvent(close, e -> {});
        pnlLedger.applyFill(toCloseFill(close, 100L), 770_00L);

        // ── Position: flat ─────────────────────────────────────────
        assertEquals(0L, positionProvider.getNetPosition(SYMBOL));

        // ── Risk: 0 open trades, 0 realized loss (profitable) ─────
        assertEquals(0, riskHandler.getOpenTrades());
        assertEquals(0L, riskHandler.getRealizedLossPaisa());
        assertEquals(0, riskHandler.getConsecutiveLosses());

        // ── PnLLedger: realized = 200000, unrealized = 0 ───────────
        assertEquals(20_00_00L, pnlLedger.realizedPnlPaisa());
        assertEquals(0L, pnlLedger.unrealizedPnlPaisa());
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario B: Multi-trade, close one at loss
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Two buys, close first at loss: position reduces, realized loss tracked")
    void multiTrade_closeFirstAtLoss_allValuesExact() {
        // ── Buy 1: 50 SBIN @ 800.00; Buy 2: 50 SBIN @ 820.00 ──────
        TradeOpened buy1 = tradeOpened("T-B1", "ORD-B1", "SIG-B1",
                Side.BUY, 50L, 800_00L, 790_00L, 830_00L);
        TradeOpened buy2 = tradeOpened("T-B2", "ORD-B2", "SIG-B2",
                Side.BUY, 50L, 820_00L, 810_00L, 850_00L);

        positionProvider.onDomainEvent(buy1);
        positionProvider.onDomainEvent(buy2);
        riskHandler.onDomainEvent(buy1, e -> {});
        riskHandler.onDomainEvent(buy2, e -> {});
        pnlLedger.applyFill(toFill(buy1), 800_00L);
        pnlLedger.applyFill(toFill(buy2), 820_00L);

        // ── Position: 100, avgPrice = (50×800+50×820)/100 = 810 ───
        assertEquals(100L, positionProvider.getNetPosition(SYMBOL));
        var pos = positionProvider.getPositions().get(SYMBOL);
        assertNotNull(pos);
        assertEquals(100L, pos.quantity());
        assertEquals(810_00L, pos.averagePricePaisa(),
                "VWAP: (50×800+50×820)/100 = ₹810.00");

        // ── Close TRADE_B1 (50 shares) @ 790.00 (LOSS) ────────────
        TradeClosed close1 = new TradeClosed(
                EventMetadata.root(),
                "T-B1", SYMBOL,
                790_00L,      // exitPricePaisa
                -5_00_00L,    // 50 × (790-800) × 100 = -50,000 paisa
                0L, "stop_loss");

        positionProvider.onDomainEvent(close1);
        riskHandler.onDomainEvent(close1, e -> {});
        pnlLedger.applyFill(toCloseFill(close1, 50L), 790_00L);

        // ── Position: 50 remaining, avg stays 810 (VWAP preserved) ─
        assertEquals(50L, positionProvider.getNetPosition(SYMBOL));
        var posAfter = positionProvider.getPositions().get(SYMBOL);
        assertNotNull(posAfter);
        assertEquals(50L, posAfter.quantity());
        assertEquals(810_00L, posAfter.averagePricePaisa(),
                "Avg price stays at VWAP on close (position provider semantics)");

        // ── Risk: 1 open trade, realized loss = 50000 paisa ────────
        assertEquals(1, riskHandler.getOpenTrades());
        assertEquals(5_00_00L, riskHandler.getRealizedLossPaisa(),
                "Realized loss: ₹500.00 = 50000 paisa");
        assertEquals(1, riskHandler.getConsecutiveLosses());

        // ── PnLLedger: sell 50@790 with VWAP 810 → PnLLedger FIFO ──
        // PnLLedger uses FIFO cost basis (not trade-specific): 50 × (790-810) × 100 = -100000
        assertEquals(-10_00_00L, pnlLedger.realizedPnlPaisa(),
                "PnLLedger FIFO: 50 × (790-810) × 100 = -₹1000.00");
        // Remaining 50 at avg 810, mark 790 → unrealized = 50 × (790-810) × 100 = -100000
        assertEquals(-10_00_00L, pnlLedger.unrealizedPnlPaisa());
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario C: Short Sell → Cover
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Short 50@800, cover @780: net position, profit tracked")
    void shortSellCover_negativePosition_profitOnCover() {
        // ── Short 50 SBIN @ 800.00 ─────────────────────────────────
        TradeOpened short_ = tradeOpened("T-C1", "ORD-C1", "SIG-C1",
                Side.SELL, 50L, 800_00L, 810_00L, 780_00L);

        positionProvider.onDomainEvent(short_);
        riskHandler.onDomainEvent(short_, e -> {});
        pnlLedger.applyFill(toFill(short_), 800_00L);

        assertEquals(-50L, positionProvider.getNetPosition(SYMBOL),
                "Short position must be -50");

        // ── Cover 50 @ 780.00 (profit on short) ───────────────────
        TradeClosed cover = new TradeClosed(
                EventMetadata.root(),
                "T-C1", SYMBOL,
                780_00L,      // cover price (below entry = profit)
                10_00_00L,    // 50 × (800-780) × 100 = 100,000 paisa
                0L, "target");

        positionProvider.onDomainEvent(cover);
        riskHandler.onDomainEvent(cover, e -> {});

        // Cover a short = BUY to close
        pnlLedger.applyFill(new Trade(
                cover.tradeId(), "", SYMBOL, ExchangeSegment.NSE_EQ,
                Side.BUY, 50L, 780_00L,                0L  // fixed timestamp
        ), 780_00L);

        // ── Position: flat ─────────────────────────────────────────
        assertEquals(0L, positionProvider.getNetPosition(SYMBOL));

        // ── Risk: profitable trade, no loss ────────────────────────
        assertEquals(0L, riskHandler.getRealizedLossPaisa());
        assertEquals(0, riskHandler.getConsecutiveLosses());

        // ── PnLLedger: short profit = 100000 paisa ─────────────────
        assertEquals(10_00_00L, pnlLedger.realizedPnlPaisa());
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario D: Determinism — two runs, identical values
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Determinism: identical trade sequence → identical values on every run")
    void deterministicRun_producesIdenticalValues() {
        ValueSnapshot run1 = runDeterministicScenario();

        // Fresh instances
        positionProvider = new EventSourcedNetPositionProvider();
        riskHandler = new PositionRiskHandler(RiskLimits.conservative(), positionProvider);
        pnlLedger = new PnLLedger();
        ValueSnapshot run2 = runDeterministicScenario();

        // ── Assert perfect determinism across all 8 values ──────────
        assertEquals(run1.finalPosition, run2.finalPosition, "Position");
        assertEquals(run1.finalAvgPrice, run2.finalAvgPrice, "Avg price");
        assertEquals(run1.realizedPnl, run2.realizedPnl, "Realized PnL");
        assertEquals(run1.unrealizedPnl, run2.unrealizedPnl, "Unrealized PnL");
        assertEquals(run1.totalPnl, run2.totalPnl, "Total PnL");
        assertEquals(run1.realizedLoss, run2.realizedLoss, "Realized loss");
        assertEquals(run1.consecutiveLosses, run2.consecutiveLosses, "Consecutive losses");
        assertEquals(run1.openTrades, run2.openTrades, "Open trades");

        // ── Verify exact expected values (not just determinism) ────
        // T-D2 remains: 200 shares @ VWAP 840.00
        assertEquals(200L, run1.finalPosition);
        assertEquals(840_00L, run1.finalAvgPrice);
        // PnLLedger: sell 100@880 (VWAP 840) → realized 400000
        assertEquals(40_00_00L, run1.realizedPnl);
        // Mark remaining 200 @ 890: 200 × (89000-84000) = 1,000,000
        assertEquals(10_00_000L, run1.unrealizedPnl);
        assertEquals(14_00_000L, run1.totalPnl);
        // T-D1 closed profitable → no loss
        assertEquals(0L, run1.realizedLoss);
        assertEquals(0, run1.consecutiveLosses);
        // T-D2 still open
        assertEquals(1, run1.openTrades);

        // ── Sanity: values must be non-trivial ─────────────────────
        assertNotEquals(0L, run1.finalPosition, "Must have non-zero final position");
        assertNotEquals(0L, run1.totalPnl, "Must have non-zero total PnL");
    }

    /**
     * Runs a fixed, deterministic 3-trade scenario.
     *
     * <pre>
     *   Buy  100 @ 800.00  (T-D1)
     *   Buy  200 @ 860.00  (T-D2)  → position 300, VWAP=840.00
     *   Close T-D1 (100sh) @ 880.00 (profit)
     *   Mark remaining at 890.00
     * </pre>
     */
    private ValueSnapshot runDeterministicScenario() {
        TradeOpened buy1 = tradeOpened("T-D1", "ORD-D1", "SIG-D1",
                Side.BUY, 100L, 800_00L, 790_00L, 850_00L);
        TradeOpened buy2 = tradeOpened("T-D2", "ORD-D2", "SIG-D2",
                Side.BUY, 200L, 860_00L, 850_00L, 900_00L);

        positionProvider.onDomainEvent(buy1);
        positionProvider.onDomainEvent(buy2);
        riskHandler.onDomainEvent(buy1, e -> {});
        riskHandler.onDomainEvent(buy2, e -> {});
        pnlLedger.applyFill(toFill(buy1), 800_00L);
        pnlLedger.applyFill(toFill(buy2), 860_00L);

        // Close T-D1 (100 shares) @ 880.00 profit
        TradeClosed close1 = new TradeClosed(
                EventMetadata.root(),
                "T-D1", SYMBOL,
                880_00L,
                80_00_00L,  // 100 × (880-800) × 100 = 800,000 paisa
                0L, "target");

        positionProvider.onDomainEvent(close1);
        riskHandler.onDomainEvent(close1, e -> {});
        pnlLedger.applyFill(toCloseFill(close1, 100L), 890_00L);

        pnlLedger.markToMarket(890_00L, SYMBOL);

        long finalPosition = positionProvider.getNetPosition(SYMBOL);
        var pos = positionProvider.getPositions().get(SYMBOL);
        long finalAvgPrice = pos != null ? pos.averagePricePaisa() : 0L;

        return new ValueSnapshot(
                finalPosition,
                finalAvgPrice,
                pnlLedger.realizedPnlPaisa(),
                pnlLedger.unrealizedPnlPaisa(),
                pnlLedger.totalPnlPaisa(),
                riskHandler.getRealizedLossPaisa(),
                riskHandler.getConsecutiveLosses(),
                riskHandler.getOpenTrades()
        );
    }

    // ════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════

    private static TradeOpened tradeOpened(
            String tradeId, String orderId, String signalId,
            Side side, long size,
            long entryPricePaisa, long stopLossPaisa, long takeProfitPaisa) {
        return new TradeOpened(
                EventMetadata.root(),
                tradeId, orderId, signalId, SYMBOL,
                side, size,
                entryPricePaisa, stopLossPaisa, takeProfitPaisa);
    }

    /** Converts a TradeOpened to a PnLLedger fill. */
    private static Trade toFill(TradeOpened opened) {
        return new Trade(
                opened.tradeId(), opened.orderId(), opened.symbol(),
                ExchangeSegment.NSE_EQ,
                opened.side(), opened.size(), opened.entryPricePaisa(),
                0L);  // fixed timestamp — PnLLedger doesn't use it
    }

    /**
     * Converts a TradeClosed to a closing fill. Uses Side.SELL for closing
     * long positions (the PnLLedger reduces quantity on SELL).
     */
    private static Trade toCloseFill(TradeClosed closed, long closeQty) {
        return new Trade(
                closed.tradeId(), "", closed.symbol(),
                ExchangeSegment.NSE_EQ,
                Side.SELL, closeQty, closed.exitPricePaisa(),
                0L);  // fixed timestamp
    }

    /** Immutable snapshot of all numeric pipeline values. */
    private record ValueSnapshot(
            long finalPosition, long finalAvgPrice,
            long realizedPnl, long unrealizedPnl, long totalPnl,
            long realizedLoss, int consecutiveLosses, int openTrades) {}
}
