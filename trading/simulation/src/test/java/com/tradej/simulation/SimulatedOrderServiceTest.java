package com.tradej.simulation;

import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimulatedOrderServiceTest {

    private MatchingEngine engine;
    private PnLLedger ledger;
    private SimulatedOrderService service;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT);
        ledger = new PnLLedger();
        service = new SimulatedOrderService(engine, ledger);
    }

    private OrderRequest req(String symbol, Side side, long qty, long pricePaisa) {
        return new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, side, qty,
                OrderType.LIMIT, pricePaisa, 0L, ProductType.INTRADAY,
                Validity.DAY, "corr-1"
        );
    }

    @Test
    void placeOrder_usesMatchingEngine() {
        engine.onTick("RELIANCE", 2500_00L);
        MatchingEngine.MatchResult result = service.placeOrder(req("RELIANCE", Side.BUY, 10, 2500_00L));
        assertFalse(result.rejected(), "Order should not be rejected when price is available");
        assertEquals(1, result.fills().size());
    }

    @Test
    void rejectedOrder_notRecordedInLedger() {
        MatchingEngine.MatchResult result = service.placeOrder(req("UNKNOWN", Side.BUY, 100, 0L));
        assertTrue(result.rejected(), "Order should be rejected when no price available");
        assertEquals(0L, ledger.realizedPnlPaisa(), "No P&L impact from rejected order");
        assertEquals(0L, ledger.unrealizedPnlPaisa());
    }

    @Test
    void fillRecordedInPnLLedger() {
        engine.onTick("TCS", 3800_00L);
        service.placeOrder(req("TCS", Side.BUY, 50, 3800_00L));
        // Buy order creates a position but no realized P&L
        assertEquals(0L, ledger.realizedPnlPaisa());
    }

    @Test
    void buyAndSell_generatesRealizedPnl() {
        engine.onTick("INFY", 1500_00L);
        service.placeOrder(req("INFY", Side.BUY, 100, 1500_00L));

        engine.onTick("INFY", 1600_00L);
        service.placeOrder(req("INFY", Side.SELL, 100, 1600_00L));

        // Buy ~150000, sell ~160000 → profit ~100 × 10000 = ~1000000 paisa
        // Volume slippage reduces this slightly
        assertTrue(ledger.realizedPnlPaisa() > 900_000L,
                "P&L should be positive and near 1000000 paisa, got " + ledger.realizedPnlPaisa());
        assertTrue(ledger.realizedPnlPaisa() < 1_100_000L,
                "P&L should not exceed 1100000 paisa, got " + ledger.realizedPnlPaisa());
    }

    @Test
    void matchingEngineAccessible() {
        assertSame(engine, service.matchingEngine());
    }

    @Test
    void pnlLedgerAccessible() {
        assertSame(ledger, service.pnlLedger());
    }
}
