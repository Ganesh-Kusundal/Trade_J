package com.tradej.strategy.example;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmaCrossStrategyTest {

    @Test
    void emitsBuyWhenFastCrossesAboveSlow() {
        SmaCrossStrategy s = new SmaCrossStrategy(3, 5);
        AtomicInteger buys = new AtomicInteger();
        AtomicInteger sells = new AtomicInteger();
        for (Candle c : trendingCandles(20, 100L, 1L)) {
            Optional<com.tradej.core.domain.event.SignalGenerated> out = s.onEvent(new CandleClosed(EventMetadata.root(), c));
            if (out.isPresent()) {
                var sig = out.get();
                if (sig.side() == com.tradej.core.domain.value.Side.BUY) buys.incrementAndGet();
                else if (sig.side() == com.tradej.core.domain.value.Side.SELL) sells.incrementAndGet();
            }
        }
        assertTrue(buys.get() > 0 || sells.get() > 0,
                "Trending series should produce at least one signal");
    }

    @Test
    void noSignalBeforeSlowWindowFills() {
        SmaCrossStrategy s = new SmaCrossStrategy(3, 5);
        Candle first = candle("RELIANCE", 100L);
        Optional<com.tradej.core.domain.event.SignalGenerated> out = s.onEvent(new CandleClosed(EventMetadata.root(), first));
        assertTrue(out.isEmpty(), "Should not emit a signal before the slow window has enough data");
    }

    @Test
    void strategyHasCorrectSubscriptionShape() {
        SmaCrossStrategy s = new SmaCrossStrategy(7, 25);
        assertEquals("sma-cross-7-25", s.name());
        assertEquals(1, s.subscribedEventTypes().size());
        assertEquals(CandleClosed.class, s.subscribedEventTypes().get(0));
    }

    @Test
    void invalidPeriodsRejected() {
        try {
            new SmaCrossStrategy(0, 5);
            assertTrue(false, "fastPeriod < 1 should be rejected");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
        try {
            new SmaCrossStrategy(5, 3);
            assertTrue(false, "slowPeriod <= fastPeriod should be rejected");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    private Candle candle(String symbol, long close) {
        long now = Instant.now().toEpochMilli();
        return new Candle(
                symbol, "1d",
                now, now + 86_400_000L,
                close - 1L, close + 1L, close - 1L, close,
                1_000_000L, true
        );
    }

    /**
     * Build a synthetic trending candle series. Starts at {@code start},
     * adds {@code step} per bar. Returns a monotonically rising series
     * that should reliably trip the SMA crossover into a BUY.
     */
    private Candle[] trendingCandles(int n, long start, long step) {
        Candle[] out = new Candle[n];
        for (int i = 0; i < n; i++) {
            long close = start + i * step;
            long now = Instant.now().plusSeconds(i * 86_400L).toEpochMilli();
            out[i] = new Candle(
                    "RELIANCE", "1d",
                    now, now + 86_400_000L,
                    close - step / 2, close + step / 4, close - step / 2, close,
                    1_000_000L, true
            );
        }
        return out;
    }
}
