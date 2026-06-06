package com.tradej.strategy.service;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("component")
class CandleAggregationServiceComponentTest {

    @Test
    void rollsCandleAndEmitsClosedEventWhenBucketChanges() {
        CandleAggregationService service = new CandleAggregationService();
        List<DomainEvent> emitted = new ArrayList<>();

        long t0 = 1_710_000_000_000L;
        long t1 = t0 + 60_000L;
        long t2 = t0 + 300_000L;

        service.onDomainEvent(new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 75_000L, 10L, 10L, t0, Optional.empty(), 0L, 0L), emitted::add);
        service.onDomainEvent(new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 75_500L, 5L, 15L, t1, Optional.empty(), 0L, 0L), emitted::add);
        service.onDomainEvent(new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 76_000L, 7L, 22L, t2, Optional.empty(), 0L, 0L), emitted::add);

        assertEquals(4, emitted.size());
        CandleDeveloping firstDeveloping = assertInstanceOf(CandleDeveloping.class, emitted.get(0));
        CandleDeveloping secondDeveloping = assertInstanceOf(CandleDeveloping.class, emitted.get(1));
        CandleClosed closed = assertInstanceOf(CandleClosed.class, emitted.get(2));
        CandleDeveloping nextDeveloping = assertInstanceOf(CandleDeveloping.class, emitted.get(3));

        assertEquals(75_000L, firstDeveloping.candle().openPaisa());
        assertEquals(75_500L, secondDeveloping.candle().closePaisa());
        assertEquals(75_500L, closed.candle().closePaisa());
        assertEquals(15L, closed.candle().volume());
        assertEquals(76_000L, nextDeveloping.candle().openPaisa());
    }

    @Test
    void buildsCandlesForMultipleIntervalsFromSingleIntervalTick() {
        CandleAggregationService service = new CandleAggregationService(List.of("1s", "5m"));
        List<DomainEvent> emitted = new ArrayList<>();

        // Use a timestamp that aligns cleanly to both 1-second and 5-minute boundaries.
        // t0 = 2024-03-10T00:00:00.000Z
        long t0  = 1_710_000_000_000L;
        long t1  = t0 + 500L;              // same 1s bucket, same 5m bucket
        long t2  = t0 + 1_500L;            // new 1s bucket, same 5m bucket
        long t3  = t0 + 300_000L;          // new 1s AND new 5m bucket

        // ── Tick 1 at t0: both intervals create initial candles ──
        service.onDomainEvent(
                new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100_00L, 10L, 1_000L, t0, Optional.empty(), 0L, 0L),
                emitted::add);

        assertEquals(2, emitted.size(), "Should emit CandleDeveloping for both intervals");
        assertEquals("1s", ((CandleDeveloping) emitted.get(0)).candle().interval());
        assertEquals("5m", ((CandleDeveloping) emitted.get(1)).candle().interval());
        assertNotNull(((CandleDeveloping) emitted.get(1)).candle());

        // ── Tick 2 at t0+500ms: same bucket for both → update ──
        emitted.clear();
        service.onDomainEvent(
                new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 101_00L, 5L, 1_005L, t1, Optional.empty(), 0L, 0L),
                emitted::add);

        assertEquals(2, emitted.size(), "Both intervals should emit developing candles");
        assertEquals(101_00L, ((CandleDeveloping) emitted.get(0)).candle().closePaisa(), "1s close should update");
        assertEquals(101_00L, ((CandleDeveloping) emitted.get(1)).candle().closePaisa(), "5m close should update");

        // ── Tick 3 at t0+1500ms: 1s bucket rolls over, 5m stays ──
        emitted.clear();
        service.onDomainEvent(
                new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 102_00L, 7L, 1_012L, t2, Optional.empty(), 0L, 0L),
                emitted::add);

        assertEquals(3, emitted.size(), "1s rollover: CandleClosed(1s) + CandleDeveloping(1s) + CandleDeveloping(5m)");
        // 1s candle closed: open=100_00, high=101_00, close=101_00, vol=15
        CandleClosed closed1s = (CandleClosed) emitted.get(0);
        assertEquals("1s", closed1s.candle().interval());
        assertEquals(100_00L, closed1s.candle().openPaisa());
        assertEquals(101_00L, closed1s.candle().highPaisa());
        assertEquals(101_00L, closed1s.candle().closePaisa());
        assertEquals(15L, closed1s.candle().volume(), "1s candle should include tick1 + tick2 volume");
        assertInstanceOf(CandleDeveloping.class, emitted.get(1));
        // 5m stays in same bucket (still developing)
        assertEquals("5m", ((CandleDeveloping) emitted.get(2)).candle().interval());

        // ── Tick 4 at t0+300s: BOTH intervals roll over ──
        emitted.clear();
        service.onDomainEvent(
                new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 103_00L, 3L, 1_015L, t3, Optional.empty(), 0L, 0L),
                emitted::add);

        assertEquals(4, emitted.size(),
                "1s rollover → CandleClosed(1s)+CandleDeveloping(1s), " +
                "5m rollover → CandleClosed(5m)+CandleDeveloping(5m)");

        // 1s bucket rollover
        assertEquals("1s", ((CandleClosed) emitted.get(0)).candle().interval());
        assertEquals("1s", ((CandleDeveloping) emitted.get(1)).candle().interval());

        // 5m bucket rollover — closed candle should have tick1-3 volume (10+5+7=22)
        CandleClosed closed5m = (CandleClosed) emitted.get(2);
        assertEquals("5m", closed5m.candle().interval());
        assertEquals(100_00L, closed5m.candle().openPaisa(), "5m open = first tick's ltp");
        assertEquals(102_00L, closed5m.candle().highPaisa(), "5m high = max(101_00, 102_00)");
        assertEquals(102_00L, closed5m.candle().closePaisa(), "5m close = tick3's ltp");
        assertEquals(22L, closed5m.candle().volume(), "5m volume sums ticks 1-3");
        // New 5m candle starts with tick4
        assertEquals("5m", ((CandleDeveloping) emitted.get(3)).candle().interval());
        assertEquals(103_00L, ((CandleDeveloping) emitted.get(3)).candle().openPaisa());
    }
}
