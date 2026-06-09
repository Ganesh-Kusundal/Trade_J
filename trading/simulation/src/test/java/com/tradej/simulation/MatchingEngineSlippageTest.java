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
class MatchingEngineSlippageTest {

    private MatchingEngine engine;

    private static OrderRequest req(String symbol, Side side, long qty, long pricePaisa, OrderType type) {
        return new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, side, qty,
                type, pricePaisa, 0L, ProductType.INTRADAY,
                Validity.DAY, "corr-1"
        );
    }

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void tickRounding_buySlippage_roundsUpToTick() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder().spreadBps(10L).build();
        var eng = new MatchingEngine(config);
        eng.onTick("SBIN", 750_00L);
        var result = eng.match(req("SBIN", Side.BUY, 10, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // 10 bps on 75000 = 75 paisa → 750_75 raw → rounded up to 750_75 (5-paisa tick)
        assertEquals(0L, result.fills().get(0).pricePaisa() % 5,
                "Fill price must be aligned to 5-paisa tick size");
        assertTrue(result.fills().get(0).pricePaisa() >= 750_00L,
                "Buy slippage should push price up or equal");
    }

    @Test
    void tickRounding_sellSlippage_roundsDownToTick() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder().spreadBps(10L).build();
        var eng = new MatchingEngine(config);
        eng.onTick("SBIN", 750_00L);
        var result = eng.match(req("SBIN", Side.SELL, 10, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(0L, result.fills().get(0).pricePaisa() % 5,
                "Fill price must be aligned to 5-paisa tick size");
        assertTrue(result.fills().get(0).pricePaisa() <= 750_00L,
                "Sell slippage should push price down or equal");
    }

    @Test
    void volumeSlippage_scalesWithQuantity() {
        var config = MatchingEngine.SlippageConfig.builder().spreadBps(0L).build();
        var eng = new MatchingEngine(config);
        eng.onTick("RELIANCE", 2500_00L);

        var smallResult = eng.match(req("RELIANCE", Side.BUY, 10, 2500_00L, OrderType.LIMIT), "ORD-S");
        var largeResult = eng.match(req("RELIANCE", Side.BUY, 500, 2500_00L, OrderType.LIMIT), "ORD-L");

        assertFalse(smallResult.rejected());
        assertFalse(largeResult.rejected());
        assertTrue(largeResult.fills().get(0).pricePaisa() >= smallResult.fills().get(0).pricePaisa(),
                "Larger order should have equal or more slippage");
    }

    @Test
    void maxSlippageBps_capsAtLimit() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .spreadBps(100L)
                .volatilitySlippageBps(100L)
                .maxSlippageBps(20L)
                .build();
        var eng = new MatchingEngine(config);
        eng.onTick("SBIN", 750_00L);
        eng.onTick("SBIN", 760_00L);
        eng.onTick("SBIN", 770_00L);
        var result = eng.match(req("SBIN", Side.BUY, 10, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // Max 20 bps on 75000 = 150 paisa max slippage
        assertTrue(result.fills().get(0).pricePaisa() <= 750_00L + 150L,
                "Fill price should not exceed max slippage cap");
    }

    @Test
    void partialFill_exactRatio() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .partialFillEnabled(true)
                .partialFillRatio(0.5)
                .minFillSize(10)
                .build();
        var eng = new MatchingEngine(config);
        eng.onTick("SBIN", 750_00L);
        var result = eng.match(req("SBIN", Side.BUY, 100, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(50, result.fills().get(0).quantity(), "50% of 100 = 50");
        assertNotNull(result.partialFillInfo());
    }

    @Test
    void partialFill_belowMinSize_fullFill() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder()
                .partialFillEnabled(true)
                .partialFillRatio(0.5)
                .minFillSize(200)
                .build();
        var eng = new MatchingEngine(config);
        eng.onTick("SBIN", 750_00L);
        var result = eng.match(req("SBIN", Side.BUY, 100, 750_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        assertEquals(100, result.fills().get(0).quantity(), "Below minFillSize → full fill");
        assertNull(result.partialFillInfo());
    }

    @Test
    void noPriceAvailable_rejected() {
        var result = engine.match(req("UNKNOWN", Side.BUY, 100, 0L, OrderType.MARKET), "ORD-1");
        assertTrue(result.rejected());
        assertTrue(result.reason().contains("No fill price"));
    }

    @Test
    void limitOrder_usesLimitAsBase() {
        engine.onTick("SBIN", 750_00L);
        var config = MatchingEngine.SlippageConfig.builder().spreadBps(10L).build();
        var eng = new MatchingEngine(config);
        eng.onTick("SBIN", 750_00L);
        var result = eng.match(req("SBIN", Side.BUY, 10, 745_00L, OrderType.LIMIT), "ORD-1");
        assertFalse(result.rejected());
        // Slippage applied to limit price (74500), not LTP (75000)
        assertTrue(result.fills().get(0).pricePaisa() >= 745_00L,
                "Fill price should be at or above limit price + slippage");
        assertTrue(result.fills().get(0).pricePaisa() < 750_00L,
                "Fill price should be below LTP since limit was lower");
    }
}
