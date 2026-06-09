package com.tradej.simulation;

import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MatchingEngineClockTest {

    private static OrderRequest req(String symbol, Side side, long qty, long pricePaisa) {
        return new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, side, qty,
                OrderType.LIMIT, pricePaisa, 0L, ProductType.INTRADAY,
                Validity.DAY, "corr-1"
        );
    }

    @Test
    void usesTradingClockForTimestamps() {
        Instant fixedTime = Instant.parse("2026-01-15T09:30:00Z");
        ReplayTradingClock clock = new ReplayTradingClock(fixedTime);
        MatchingEngine engine = new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT, clock);

        engine.onTick("SBIN", 750_00L);
        MatchingEngine.MatchResult result = engine.match(req("SBIN", Side.BUY, 10, 750_00L), "ORD-1");

        assertFalse(result.rejected());
        assertEquals(fixedTime.toEpochMilli(), result.order().exchangeTimeMs(),
                "Order timestamp must use TradingClock, not System.currentTimeMillis()");
        assertEquals(fixedTime.toEpochMilli(), result.fills().get(0).exchangeTimeMs(),
                "Fill timestamp must use TradingClock");
    }

    @Test
    void backtestTimestamps_areDeterministic() {
        Instant fixedTime = Instant.parse("2026-03-20T10:00:00Z");

        MatchingEngine engine1 = new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT, new ReplayTradingClock(fixedTime));
        engine1.onTick("RELIANCE", 250_000L);
        MatchingEngine.MatchResult result1 = engine1.match(req("RELIANCE", Side.BUY, 5, 250_000L), "ORD-A");

        MatchingEngine engine2 = new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT, new ReplayTradingClock(fixedTime));
        engine2.onTick("RELIANCE", 250_000L);
        MatchingEngine.MatchResult result2 = engine2.match(req("RELIANCE", Side.BUY, 5, 250_000L), "ORD-B");

        assertEquals(result1.order().exchangeTimeMs(), result2.order().exchangeTimeMs(),
                "Same clock time must produce identical timestamps across runs");
        assertEquals(result1.fills().get(0).exchangeTimeMs(), result2.fills().get(0).exchangeTimeMs());
    }

    @Test
    void rejectedOrderAlsoUsesClock() {
        Instant fixedTime = Instant.parse("2026-06-01T04:00:00Z");
        ReplayTradingClock clock = new ReplayTradingClock(fixedTime);
        MatchingEngine engine = new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT, clock);

        MatchingEngine.MatchResult result = engine.match(req("UNKNOWN", Side.BUY, 100, 0L), "ORD-REJ");

        assertTrue(result.rejected());
        assertEquals(fixedTime.toEpochMilli(), result.order().exchangeTimeMs(),
                "Rejected order timestamp must use TradingClock");
    }

    @Test
    void defaultConstructorUsesWallClock() {
        MatchingEngine engine = new MatchingEngine();
        assertNotNull(engine.clock(), "Default constructor should provide a LiveTradingClock");

        engine.onTick("SBIN", 750_00L);
        long beforeMs = System.currentTimeMillis();
        MatchingEngine.MatchResult result = engine.match(req("SBIN", Side.BUY, 10, 750_00L), "ORD-1");
        long afterMs = System.currentTimeMillis();

        assertTrue(result.order().exchangeTimeMs() >= beforeMs && result.order().exchangeTimeMs() <= afterMs,
                "Default clock should produce wall-clock timestamps");
    }
}
