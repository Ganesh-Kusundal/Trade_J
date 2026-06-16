package com.tradej.app.e2e;

import com.tradej.core.testsupport.TestSymbols;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.historical.ingest.resample.CandleResampler;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14: Candle Consistency Verification.
 *
 * <p>Verifies that candles computed from raw ticks produce identical
 * Open/High/Low/Close/Volume/Timestamp values regardless of path:
 *
 * <pre>
 *   Raw Ticks
 *     ├── CandleAggregationService (live)   → CandleClosed
 *     ├── CandleAggregationService (replay) → CandleClosed
 *     └── CandleResampler (1m → 5m)         → CandleClosed
 * </pre>
 *
 * <p>All candles must be identical. If they differ, signals, backtests,
 * and analytics built on those candles are unreliable.
 *
 * <p>All timestamps are within NSE trading hours (09:15–15:30 IST) on a
 * fixed trading date to ensure proper session-aligned bucketing.
 */
@Tag("chaos")
@DisplayName("Candle Consistency: ticks → candles across live/replay/resample")
class CandleConsistencyCertificationTest {

    private static final ZoneId IST = CandleBucketPolicy.IST;
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 15);
    private static final String SYMBOL = TestSymbols.SBIN;
    private static final String SYMBOL2 = TestSymbols.RELIANCE;

    // ════════════════════════════════════════════════════════════════
    // Scenario A: Live-Live parity — two instances, same ticks, same candles
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Two CandleAggregationService instances produce identical 1m candles from same ticks")
    void identicalTicksProduceIdenticalCandles() {
        List<Candle> candles1 = captureClosedCandles("1m", generateUptrendTicks(10));
        List<Candle> candles2 = captureClosedCandles("1m", generateUptrendTicks(10));

        assertCandlesEqual(candles1, candles2, "Two live instances must produce identical candles");
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario B: Resampler parity — 1m resampled = direct 5m aggregation
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1m candles resampled to 5m match directly aggregated 5m candles")
    void resampledMatchesDirectAggregation() {
        // 16 ticks (09:15–09:30) → 15 closed 1m candles → 3 complete 5m buckets, 3 direct 5m candles
        List<MarketTickEvent> ticks = generateUptrendTicks(16);

        // Path 1: Direct 5m aggregation
        List<Candle> direct5m = captureClosedCandles("5m", ticks);

        // Path 2: 1m aggregation, then resample to 5m
        List<Candle> oneMin = captureClosedCandles("1m", ticks);
        List<Candle> resampled5m = CandleResampler.resample(oneMin, "5m");

        assertFalse(direct5m.isEmpty(), "Must produce at least one 5m candle");
        assertFalse(resampled5m.isEmpty(), "Resampler must produce at least one 5m candle");

        // Resampled candles should match direct aggregation in count and values
        assertEquals(direct5m.size(), resampled5m.size(),
                "Resampled count must match direct aggregation count");

        for (int i = 0; i < direct5m.size(); i++) {
            Candle direct = direct5m.get(i);
            Candle resampled = resampled5m.get(i);

            assertEquals(direct.startTimeMs(), resampled.startTimeMs(),
                    "Resampled startTimeMs[" + i + "] must match direct");
            assertEquals(direct.openPaisa(), resampled.openPaisa(),
                    "Resampled open[" + i + "] must match direct");
            // High = max of all 1m highs in the bucket
            assertEquals(direct.highPaisa(), resampled.highPaisa(),
                    "Resampled high[" + i + "] must match direct");
            // Low = min of all 1m lows in the bucket
            assertEquals(direct.lowPaisa(), resampled.lowPaisa(),
                    "Resampled low[" + i + "] must match direct");
            assertEquals(direct.closePaisa(), resampled.closePaisa(),
                    "Resampled close[" + i + "] must match direct");
            assertEquals(direct.volume(), resampled.volume(),
                    "Resampled volume[" + i + "] must match direct");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario C: Live vs Replay — identical candles with ReplayTradingClock
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ReplayTradingClock fallback path produces identical candles to exchange-timestamp path")
    void clockFallbackPathProducesIdenticalCandles() {
        // Use exchangeTimestampMs=0 AND metadata timestampMs=0 so the
        // TradingClock fallback in bucketStart() is actually exercised.
        // EventMetadata.root() uses wall-clock time, so we construct
        // zero-timestamp metadata directly.
        long t0 = sessionOpenMs();
        List<MarketTickEvent> liveTicks = generateUptrendTicks(10);

        // Create ticks with exchangeTimestampMs=0 AND metadata.timestampMs=0
        EventMetadata zeroMeta = new EventMetadata("zero", 0L, 0L, 0L, null, 1);
        List<MarketTickEvent> clockTicks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            clockTicks.add(new MarketTickEvent(
                    zeroMeta,
                    0L, SYMBOL, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                    100_00L + i * 100L, 10L, 0L,
                    0L,  // exchangeTimestampMs=0 → triggers clock fallback
                    Optional.empty(), 0L, 0L));
        }

        // Live path: exchange timestamps used directly
        List<Candle> liveCandles = captureClosedCandles("1m", liveTicks);

        // Replay path: ReplayTradingClock advanced per tick
        ReplayTradingClock replayClock = new ReplayTradingClock(Instant.ofEpochMilli(t0));
        List<Candle> replayCandles = new ArrayList<>();
        CandleAggregationService replayService = new CandleAggregationService(
                List.of("1m"), replayClock);

        for (int i = 0; i < clockTicks.size(); i++) {
            replayClock.advanceTo(Instant.ofEpochMilli(t0 + i * 60_000L));
            replayService.onDomainEvent(clockTicks.get(i), event -> {
                if (event instanceof CandleClosed closed) {
                    replayCandles.add(closed.candle());
                }
            });
        }

        assertCandlesEqual(liveCandles, replayCandles,
                "Clock fallback path must produce identical candles to exchange-timestamp path");
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario D: Multi-symbol — candles don't cross-contaminate
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Interleaved ticks for two symbols produce independent, correct candles")
    void multiSymbolCandlesDontCrossContaminate() {
        long t0 = sessionOpenMs();

        // Interleave ticks for two symbols
        List<MarketTickEvent> allTicks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long ts = t0 + i * 60_000L;  // 1 tick per minute per symbol
            // SBIN: uptrend 100 → 109
            allTicks.add(tick(SYMBOL, 100_00L + i * 100L, 10L, ts));
            // RELIANCE: downtrend 250 → 241
            allTicks.add(tick(SYMBOL2, 250_00L - i * 100L, 20L, ts));
        }

        List<Candle> allCandles = captureClosedCandles("1m", allTicks);

        List<Candle> sbinCandles = allCandles.stream()
                .filter(c -> c.symbol().equals(SYMBOL)).toList();
        List<Candle> relianceCandles = allCandles.stream()
                .filter(c -> c.symbol().equals(SYMBOL2)).toList();

        // 10 interleaved ticks per symbol → 9 closed 1m candles each (last tick stays developing)
        assertEquals(9, sbinCandles.size(), "10 ticks → 9 closed 1m candles for SBIN");
        assertEquals(9, relianceCandles.size(), "10 ticks → 9 closed 1m candles for RELIANCE");

        // SBIN candles: open/close should trend up
        Candle firstSbin = sbinCandles.getFirst();
        Candle lastSbin = sbinCandles.getLast();
        assertTrue(lastSbin.closePaisa() > firstSbin.openPaisa(),
                "SBIN should trend up (last close > first open)");

        // RELIANCE candles: open/close should trend down
        Candle firstReliance = relianceCandles.getFirst();
        Candle lastReliance = relianceCandles.getLast();
        assertTrue(lastReliance.closePaisa() < firstReliance.openPaisa(),
                "RELIANCE should trend down (last close < first open)");

        // No cross-contamination: SBIN volume = 10/min, RELIANCE = 20/min
        for (Candle c : sbinCandles) {
            assertEquals(10L, c.volume(), "SBIN volume per candle must be 10 (1 tick × 10 qty)");
        }
        for (Candle c : relianceCandles) {
            assertEquals(20L, c.volume(), "RELIANCE volume per candle must be 20 (1 tick × 20 qty)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Test helpers
    // ════════════════════════════════════════════════════════════════

    /** Feeds ticks through a new CandleAggregationService and returns CandleClosed candles. */
    private List<Candle> captureClosedCandles(String interval, List<MarketTickEvent> ticks) {
        return captureClosedCandlesWithClock(interval, ticks, new LiveTradingClock());
    }

    /** Same as above but with a specific TradingClock for replay determinism. */
    private List<Candle> captureClosedCandlesWithClock(
            String interval,
            List<MarketTickEvent> ticks,
            com.tradej.core.domain.time.TradingClock clock) {
        CandleAggregationService service = new CandleAggregationService(List.of(interval), clock);
        List<Candle> closedCandles = new ArrayList<>();

        for (MarketTickEvent tick : ticks) {
            service.onDomainEvent(tick, event -> {
                if (event instanceof CandleClosed closed) {
                    closedCandles.add(closed.candle());
                }
            });
        }

        return closedCandles;
    }

    /**
     * Generates deterministic uptrend ticks at 1-minute intervals within
     * NSE trading hours, starting at 09:15 IST on {@link #TRADE_DATE}.
     *
     * <p>Prices range from 100.00 to (100 + minutes). Each tick increases
     * LTP by 1 rupee and volume by 10. This produces predictable OHLC:
     * open = first price in bucket, high = last, low = first, close = last.
     */
    private List<MarketTickEvent> generateUptrendTicks(int minutes) {
        List<MarketTickEvent> ticks = new ArrayList<>();
        long t0 = sessionOpenMs();

        for (int i = 0; i < minutes; i++) {
            long ts = t0 + i * 60_000L;
            long ltp = 100_00L + i * 100L;  // 100.00, 100.01, 100.02, ...
            ticks.add(tick(SYMBOL, ltp, 10L, ts));
        }
        return ticks;
    }

    private static MarketTickEvent tick(String symbol, long ltpPaisa, long qty, long exchangeTimestampMs) {
        return new MarketTickEvent(
                EventMetadata.root(),
                0L, symbol, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                ltpPaisa, qty, 0L,
                exchangeTimestampMs,
                Optional.empty(), 0L, 0L);
    }

    private static long sessionOpenMs() {
        return TRADE_DATE.atTime(LocalTime.of(9, 15))
                .atZone(IST).toInstant().toEpochMilli();
    }

    /** Asserts two candle lists are identical in size and every O/H/L/C/V/T field. */
    private static void assertCandlesEqual(List<Candle> expected, List<Candle> actual, String message) {
        assertEquals(expected.size(), actual.size(), message + " — size mismatch");

        for (int i = 0; i < expected.size(); i++) {
            Candle e = expected.get(i);
            Candle a = actual.get(i);
            assertEquals(e.symbol(), a.symbol(), message + " symbol[" + i + "]");
            assertEquals(e.interval(), a.interval(), message + " interval[" + i + "]");
            assertEquals(e.startTimeMs(), a.startTimeMs(), message + " startTimeMs[" + i + "]");
            assertEquals(e.endTimeMs(), a.endTimeMs(), message + " endTimeMs[" + i + "]");
            assertEquals(e.openPaisa(), a.openPaisa(), message + " open[" + i + "]");
            assertEquals(e.highPaisa(), a.highPaisa(), message + " high[" + i + "]");
            assertEquals(e.lowPaisa(), a.lowPaisa(), message + " low[" + i + "]");
            assertEquals(e.closePaisa(), a.closePaisa(), message + " close[" + i + "]");
            assertEquals(e.volume(), a.volume(), message + " volume[" + i + "]");
        }
    }
}
