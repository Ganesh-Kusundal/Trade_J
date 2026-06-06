package com.tradej.simulation;

import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    private OrderRequest req(String symbol, Side side, long qty, long pricePaisa, OrderType orderType) {
        return new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, side, qty,
                orderType, pricePaisa, 0L, ProductType.INTRADAY,
                com.tradej.core.domain.value.Validity.DAY, "sig-1"
        );
    }

    @Test
    void noSlippageByDefault() {
        engine.onTick("SBIN", 750_00L);
        var result = engine.match(req("SBIN", Side.BUY, 10, 750_00L, OrderType.MARKET), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(1, result.fills().size());
        assertEquals(750_00L, result.fills().get(0).pricePaisa());
        assertNull(result.partialFillInfo());
    }

    @Test
    void spreadSlippageAppliedToBuyOrders() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .spreadBps(10L)  // 0.10% = 75 paisa on 75000
                .build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.BUY, 10, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(750_75L, result.fills().get(0).pricePaisa());
    }

    @Test
    void spreadSlippageAppliedToSellOrders() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .spreadBps(10L)
                .build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.SELL, 10, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(749_25L, result.fills().get(0).pricePaisa());
    }

    @Test
    void limitOrderPriceIsUsedAsBaseWhenProvided() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder().spreadBps(10L).build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.BUY, 10, 745_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // 10 bps on 74500 = 74.5 paisa → integer division truncates to 74 → 745_74L raw → rounded up to 745_75L (5-paisa tick size)
        assertEquals(745_75L, result.fills().get(0).pricePaisa());
    }

    @Test
    void volatilitySlippageScaledByRecentPriceMovement() {
        // Large tick deltas to generate meaningful variance in paisa
        engine.onTick("SBIN", 75_000_00L);
        engine.onTick("SBIN", 75_500_00L);
        engine.onTick("SBIN", 76_000_00L);
        engine.onTick("SBIN", 76_500_00L);
        engine.onTick("SBIN", 77_000_00L);

        var config = MatchingEngine.SlippageConfig.builder()
                .spreadBps(5L)
                .volatilitySlippageBps(10L)
                .maxSlippageBps(10L)
                .build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.BUY, 100, 77_000_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // BUY with any positive slippage config should push price above base
        assertTrue(result.fills().get(0).pricePaisa() > 77_000_00L,
                "Slippage should be applied over base price for BUY");
    }

    @Test
    void maxSlippageBpsCapsTotalSlippage() {
        engine.onTick("SBIN", 750_00L);
        engine.onTick("SBIN", 760_00L);
        engine.onTick("SBIN", 770_00L);
        engine.onTick("SBIN", 780_00L);
        engine.onTick("SBIN", 790_00L);

        var config = MatchingEngine.SlippageConfig.builder()
                .spreadBps(100L)
                .volatilitySlippageBps(100L)
                .maxSlippageBps(20L)
                .build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.BUY, 10, 790_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // Max 20 bps on 79000 = 158 paisa → total price = 79158 raw → rounded up to 791_60L (5-paisa tick size)
        assertEquals(791_60L, result.fills().get(0).pricePaisa());
    }

    @Test
    void partialFillTriggersWhenEnabledAndSizeExceedsMinFillSize() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .spreadBps(10L)
                .partialFillEnabled(true)
                .partialFillRatio(0.7)
                .minFillSize(50)
                .build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.BUY, 100, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(70, result.fills().get(0).quantity(), "70% of 100 = 70");
        assertNotNull(result.partialFillInfo());
        assertTrue(result.partialFillInfo().contains("70/100"));
    }

    @Test
    void noPartialFillWhenBelowMinFillSize() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .partialFillEnabled(true)
                .partialFillRatio(0.7)
                .minFillSize(200)
                .build();
        var engine = new MatchingEngine(config);
        var result = engine.match(req("SBIN", Side.BUY, 100, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(100, result.fills().get(0).quantity());
        assertNull(result.partialFillInfo());
    }

    @Test
    void conservativePresetHasSpreadAndPartialFill() {
        engine.onTick("SBIN", 750_00L);
        var engine2 = new MatchingEngine(MatchingEngine.SlippageConfig.CONSERVATIVE);
        var result = engine2.match(req("SBIN", Side.BUY, 100, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // 10 bps spread + 2 bps volume scaling = 12 bps total on 750_00L = 90 paisa -> 750_90L
        assertEquals(750_90L, result.fills().get(0).pricePaisa());
        assertEquals(70, result.fills().get(0).quantity());
        assertNotNull(result.partialFillInfo());
    }

    @Test
    void noFillWhenNoPriceAvailable() {
        var result = engine.match(req("UNKNOWN", Side.BUY, 100, 0L, OrderType.MARKET), "ORD-1");
        assertTrue(result.rejected());
        assertTrue(result.reason().contains("No fill price"));
    }

    @Test
    void fillPriceFallsBackToRequestPriceWhenNoMarketTick() {
        var engine2 = new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT);
        var result = engine2.match(req("SBIN", Side.BUY, 100, 755_00L, OrderType.MARKET), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(755_00L, result.fills().get(0).pricePaisa());
    }
}