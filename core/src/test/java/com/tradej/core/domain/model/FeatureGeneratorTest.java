package com.tradej.core.domain.model;

import com.tradej.core.testsupport.TestSymbols;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class FeatureGeneratorTest {

    @Test
    void computeReturnsEmptyForZeroCandles() {
        assertFalse(FeatureGenerator.compute(List.of(), 14).isPresent());
    }

    @Test
    void computeReturnsEmptyForTooFewCandles() {
        List<Candle> candles = List.of(
                candle(TestSymbols.SBIN, 1_710_000_000_000L, 75_000L, 75_200L, 10_000L)
        );
        assertFalse(FeatureGenerator.compute(candles, 14).isPresent());
    }

    @Test
    void computeReturnsFeaturesForSufficientCandles() {
        List<Candle> candles = sixCandles(TestSymbols.SBIN);

        Optional<FeatureVector> result = FeatureGenerator.compute(candles, 14);

        assertTrue(result.isPresent());
        FeatureVector fv = result.get();
        assertEquals(TestSymbols.SBIN, fv.symbol());
        assertEquals("5m", fv.interval());
        assertEquals(1_710_001_800_000L, fv.timestampMs());
        assertTrue(fv.rsi() > 0 && fv.rsi() < 100);
        assertTrue(fv.vwapPaisa() > 0);
        assertTrue(fv.ema5Paisa() > 0);
        assertTrue(fv.ema9Paisa() > 0);
        assertTrue(fv.ema21Paisa() > 0);
        assertTrue(fv.sma20Paisa() > 0);
        assertTrue(fv.sma50Paisa() > 0);
    }

    @Test
    void rsiOnUpwardTrendIsBullish() {
        List<Candle> candles = monotonicCandles(TestSymbols.RELIANCE, 100_000L, 500L, 200L, 15);

        Optional<FeatureVector> fv = FeatureGenerator.compute(candles, 14);

        assertTrue(fv.isPresent());
        assertTrue(fv.get().rsi() > 50, "RSI should be bullish (>50) in an upward trend: " + fv.get().rsi());
    }

    @Test
    void rsiOnDownwardTrendIsBearish() {
        List<Candle> candles = monotonicCandles(TestSymbols.RELIANCE, 100_000L, -500L, -200L, 15);

        Optional<FeatureVector> fv = FeatureGenerator.compute(candles, 14);

        assertTrue(fv.isPresent());
        assertTrue(fv.get().rsi() < 50, "RSI should be bearish (<50) in a downward trend: " + fv.get().rsi());
    }

    @Test
    void rsiReturns100ForNoLosses() {
        long[] closes = {100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L,
                         110L, 111L, 112L, 113L, 114L, 115L};
        assertEquals(100.0, FeatureGenerator.computeRsi(closes, 14), 0.001, "RSI should be 100 with no losing periods");
    }

    @Test
    void rsiReturns50ForInsufficientData() {
        long[] closes = {100L, 101L, 102L};
        assertEquals(50.0, FeatureGenerator.computeRsi(closes, 14), "RSI should be 50 with insufficient data");
    }

    @Test
    void emaWithInsufficientDataReturnsLatestClose() {
        long[] closes = {100L, 101L};
        assertEquals(101L, FeatureGenerator.computeEma(closes, 5));
    }

    @Test
    void emaConvergesWithSufficientData() {
        long[] closes = {100L, 102L, 104L, 106L, 108L, 110L, 112L, 114L};
        long ema5 = FeatureGenerator.computeEma(closes, 5);
        assertTrue(ema5 > 100L && ema5 < 115L, "EMA5 should be between 100 and 115: " + ema5);
    }

    @Test
    void smaReturnsCorrectAverage() {
        long[] closes = {100L, 200L, 300L, 400L, 500L};
        assertEquals(300L, FeatureGenerator.computeSma(closes, 5));
    }

    @Test
    void smaWithInsufficientDataReturnsLatestClose() {
        long[] closes = {100L};
        assertEquals(100L, FeatureGenerator.computeSma(closes, 5));
    }

    @Test
    void vwapComputedCorrectly() {
        long[] closes = {100L, 200L, 300L};
        long[] volumes = {1000L, 2000L, 3000L};
        // (100*1000 + 200*2000 + 300*3000) / (1000 + 2000 + 3000) = (100k + 400k + 900k) / 6000 = 1_400_000 / 6000 ≈ 233
        assertEquals(233L, FeatureGenerator.computeVwap(closes, volumes));
    }

    @Test
    void vwapWithZeroVolumeReturnsLatestClose() {
        long[] closes = {100L, 200L, 300L};
        long[] volumes = {0L, 0L, 0L};
        assertEquals(300L, FeatureGenerator.computeVwap(closes, volumes));
    }

    @Test
    void volatilityIsZeroForConstantPrices() {
        long[] closes = {100L, 100L, 100L, 100L, 100L};
        assertEquals(0.0, FeatureGenerator.computeVolatility(closes), 0.001);
    }

    @Test
    void volatilityIsPositiveForVaryingPrices() {
        long[] closes = {100L, 200L, 100L, 200L, 100L, 200L};
        assertTrue(FeatureGenerator.computeVolatility(closes) > 0.0);
    }

    @Test
    void volumeImbalancePositiveWhenBullish() {
        long[] opens = {100L, 100L, 100L};
        long[] closes = {110L, 110L, 110L};
        long[] volumes = {1000L, 2000L, 3000L};
        assertEquals(6000L, FeatureGenerator.computeVolumeImbalance(opens, closes, volumes));
    }

    @Test
    void volumeImbalanceNegativeWhenBearish() {
        long[] opens = {100L, 100L, 100L};
        long[] closes = {90L, 90L, 90L};
        long[] volumes = {1000L, 2000L, 3000L};
        assertEquals(-6000L, FeatureGenerator.computeVolumeImbalance(opens, closes, volumes));
    }

    @Test
    void computeIsDeterministic() {
        List<Candle> candles = sixCandles(TestSymbols.SBIN);
        Optional<FeatureVector> a = FeatureGenerator.compute(candles, 14);
        Optional<FeatureVector> b = FeatureGenerator.compute(candles, 14);
        assertTrue(a.isPresent());
        assertTrue(b.isPresent());
        assertEquals(a.get(), b.get(), "Same input should produce identical FeatureVector");
    }

    // ── Helpers ──

    private static List<Candle> sixCandles(String symbol) {
        return List.of(
                candle(symbol, 1_710_000_000_000L, 75_000L, 75_200L, 10_000L),
                candle(symbol, 1_710_000_300_000L, 75_200L, 75_600L, 15_000L),
                candle(symbol, 1_710_000_600_000L, 75_600L, 76_000L, 12_000L),
                candle(symbol, 1_710_000_900_000L, 76_000L, 76_300L, 18_000L),
                candle(symbol, 1_710_001_200_000L, 76_300L, 76_500L, 14_000L),
                candle(symbol, 1_710_001_500_000L, 76_500L, 76_800L, 20_000L)
        );
    }

    /** Generate candles with a monotonic price trend for RSI testing. */
    private static List<Candle> monotonicCandles(String symbol, long base, long openStep, long closeStep, int count) {
        List<Candle> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long open = base + (i * openStep);
            long close = base + (i * openStep) + closeStep;
            result.add(new Candle(
                    symbol, "5m",
                    1_710_000_000_000L + (i * 300_000L),
                    1_710_000_000_000L + (i * 300_000L) + 300_000L,
                    open, Math.max(open, close) + 100L, Math.min(open, close) - 50L,
                    close, 10_000L, true
            ));
        }
        return result;
    }

    private static Candle candle(String symbol, long startMs, long open, long close, long volume) {
        return new Candle(symbol, "5m", startMs, startMs + 300_000L,
                open, Math.max(open, close) + 100L, Math.min(open, close) - 50L,
                close, volume, true);
    }
}
