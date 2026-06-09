package com.tradej.simulation;

import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PnLLedgerTest {

    private PnLLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new PnLLedger();
    }

    private Trade fill(String symbol, Side side, long qty, long pricePaisa) {
        return new Trade(
                "FILL-" + UUID.randomUUID(), "ORD-1", symbol,
                ExchangeSegment.NSE_EQ, side, qty, pricePaisa,
                System.currentTimeMillis()
        );
    }

    @Test
    void singleBuy_noRealizedPnl() {
        ledger.applyFill(fill("RELIANCE", Side.BUY, 100, 2500_00L), 2500_00L);
        assertEquals(0L, ledger.realizedPnlPaisa(), "No realized P&L from a single buy");
        assertEquals(0L, ledger.unrealizedPnlPaisa(), "No unrealized P&L when mark = entry");
    }

    @Test
    void buyThenSell_realizedPnl() {
        ledger.applyFill(fill("RELIANCE", Side.BUY, 100, 2500_00L), 2500_00L);
        ledger.applyFill(fill("RELIANCE", Side.SELL, 100, 2600_00L), 2600_00L);
        // Bought at 250000 paisa, sold at 260000 paisa → profit = 100 × 10000 = 1_000_000 paisa
        assertEquals(1_000_000L, ledger.realizedPnlPaisa(),
                "Realized P&L should be 100 × (260000 - 250000) = 1000000 paisa");
    }

    @Test
    void multipleFills_vwapEntry() {
        ledger.applyFill(fill("TCS", Side.BUY, 50, 3800_00L), 3800_00L);
        ledger.applyFill(fill("TCS", Side.BUY, 50, 3900_00L), 3900_00L);
        // Average entry = (50×380000 + 50×390000) / 100 = 385000 paisa
        // Unrealized at mark 390000 = 100 × (390000 - 385000) = 500000 paisa
        assertEquals(0L, ledger.realizedPnlPaisa(), "No realized P&L when only buying");
        assertEquals(500_000L, ledger.unrealizedPnlPaisa(),
                "Unrealized P&L should be 100 × 5000 = 500000 paisa");
    }

    @Test
    void partialClose_partialRealizedPnl() {
        ledger.applyFill(fill("INFY", Side.BUY, 100, 1500_00L), 1500_00L);
        ledger.applyFill(fill("INFY", Side.SELL, 50, 1600_00L), 1600_00L);
        // Sold 50 of 100 at 160000, entry was 150000 → realized = 50 × 10000 = 500000 paisa
        assertEquals(500_000L, ledger.realizedPnlPaisa(),
                "Partial close: 50 × (160000 - 150000) = 500000 paisa realized");
        // Remaining 50 at avgPrice 150000, mark at 160000 → unrealized = 50 × 10000 = 500000
        assertEquals(500_000L, ledger.unrealizedPnlPaisa(),
                "Remaining 50 × (160000 - 150000) = 500000 paisa unrealized");
    }

    @Test
    void squareOff_fullRealization() {
        ledger.applyFill(fill("SBIN", Side.BUY, 200, 750_00L), 750_00L);
        ledger.applyFill(fill("SBIN", Side.SELL, 200, 780_00L), 780_00L);
        // Full square off: 200 × (78000 - 75000) = 200 × 3000 = 600000 paisa
        assertEquals(600_000L, ledger.realizedPnlPaisa(),
                "Full square off: 200 × (78000 - 75000) = 600000 paisa");
        assertEquals(0L, ledger.unrealizedPnlPaisa(),
                "No unrealized P&L after full square off");
    }

    @Test
    void totalPnl_isSumOfRealizedAndUnrealized() {
        ledger.applyFill(fill("HDFCBANK", Side.BUY, 100, 1650_00L), 1650_00L);
        ledger.applyFill(fill("HDFCBANK", Side.SELL, 50, 1700_00L), 1700_00L);
        assertEquals(ledger.realizedPnlPaisa() + ledger.unrealizedPnlPaisa(),
                ledger.totalPnlPaisa());
    }

    @Test
    void lossTrade_negativeRealizedPnl() {
        ledger.applyFill(fill("WIPRO", Side.BUY, 100, 450_00L), 450_00L);
        ledger.applyFill(fill("WIPRO", Side.SELL, 100, 420_00L), 420_00L);
        // Loss: 100 × (42000 - 45000) = -300000 paisa
        assertEquals(-300_000L, ledger.realizedPnlPaisa(),
                "Loss: 100 × (42000 - 45000) = -300000 paisa");
    }
}
