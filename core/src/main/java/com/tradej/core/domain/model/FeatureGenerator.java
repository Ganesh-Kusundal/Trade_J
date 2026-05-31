package com.tradej.core.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Pure-function feature generator that computes a {@link FeatureVector}
 * from a list of chronological {@link Candle} instances.
 *
 * <p>This class is stateless and has no external dependencies — same candles
 * always produce the same features. It is suitable for use inside a feature
 * store, a streaming pipeline, or a batch back-testing context.
 */
public final class FeatureGenerator {

    private FeatureGenerator() {
        // utility class
    }

    /**
     * Compute a feature vector from the given candles.
     *
     * @param candles  chronological candles (oldest first)
     * @param lookback minimum number of candles expected for meaningful features
     * @return a {@link FeatureVector} if {@code candles.size() >= min(lookback, 5)},
     *         otherwise {@link Optional#empty()}
     */
    public static Optional<FeatureVector> compute(List<Candle> candles, int lookback) {
        int n = candles.size();
        if (n < Math.min(lookback, 5)) {
            return Optional.empty();
        }

        long timestampMs = candles.get(n - 1).endTimeMs();
        String symbol = candles.get(0).symbol();
        String interval = candles.get(0).interval();

        // Extract parallel price/volume arrays
        long[] closes = candles.stream().mapToLong(Candle::closePaisa).toArray();
        long[] volumes = candles.stream().mapToLong(Candle::volume).toArray();
        long[] opens = candles.stream().mapToLong(Candle::openPaisa).toArray();

        double rsi = computeRsi(closes, 14);
        long ema5 = computeEma(closes, 5);
        long ema9 = computeEma(closes, 9);
        long ema21 = computeEma(closes, 21);
        long sma20 = computeSma(closes, Math.min(20, n));
        long sma50 = computeSma(closes, Math.min(50, n));
        long vwap = computeVwap(closes, volumes);
        double volatility = computeVolatility(closes);
        long volumeImbalance = computeVolumeImbalance(opens, closes, volumes);

        return Optional.of(new FeatureVector(
                symbol, interval, timestampMs,
                rsi, ema5, ema9, ema21, sma20, sma50,
                vwap, volatility, volumeImbalance, 0L
        ));
    }

    // ── Individual feature computations ──

    /**
     * Relative Strength Index using Wilder's smoothing.
     *
     * @param closes close prices, chronological
     * @param period  typically 14
     * @return RSI in range [0, 100], or 50.0 if insufficient data
     */
    public static double computeRsi(long[] closes, int period) {
        if (closes.length < period + 1) {
            return 50.0;
        }

        double totalGain = 0.0;
        double totalLoss = 0.0;

        for (int i = 1; i <= period; i++) {
            double change = (double) (closes[i] - closes[i - 1]);
            if (change >= 0) {
                totalGain += change;
            } else {
                totalLoss += -change;
            }
        }

        double avgGain = totalGain / period;
        double avgLoss = totalLoss / period;

        for (int i = period + 1; i < closes.length; i++) {
            double change = (double) (closes[i] - closes[i - 1]);
            avgGain = (avgGain * (period - 1) + Math.max(change, 0.0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-change, 0.0)) / period;
        }

        if (avgLoss == 0.0) {
            return 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Exponential Moving Average with SMA seed.
     *
     * @param closes close prices, chronological
     * @param period  e.g. 5, 9, 21
     * @return EMA value, or the latest close if insufficient data
     */
    public static long computeEma(long[] closes, int period) {
        if (closes.length < period) {
            return closes[closes.length - 1];
        }

        double sma = 0.0;
        for (int i = 0; i < period; i++) {
            sma += closes[i];
        }
        sma /= period;

        double k = 2.0 / (period + 1);
        double ema = sma;

        for (int i = period; i < closes.length; i++) {
            ema = closes[i] * k + ema * (1.0 - k);
        }

        return Math.round(ema);
    }

    /**
     * Simple Moving Average over the most recent {@code period} closes.
     *
     * @param closes close prices, chronological
     * @param period  lookback window size
     * @return SMA value, or the latest close if insufficient data
     */
    public static long computeSma(long[] closes, int period) {
        if (closes.length < period) {
            return closes[closes.length - 1];
        }
        long sum = 0;
        int start = closes.length - period;
        for (int i = start; i < closes.length; i++) {
            sum += closes[i];
        }
        return sum / period;
    }

    /**
     * Volume Weighted Average Price across all candles.
     *
     * @param closes  close prices, chronological
     * @param volumes corresponding volumes
     * @return VWAP value, or the latest close if total volume is zero
     */
    public static long computeVwap(long[] closes, long[] volumes) {
        if (closes.length == 0) {
            return 0L;
        }
        java.math.BigInteger sumPriceVol = java.math.BigInteger.ZERO;
        java.math.BigInteger sumVol = java.math.BigInteger.ZERO;
        for (int i = 0; i < closes.length; i++) {
            sumPriceVol = sumPriceVol.add(
                    java.math.BigInteger.valueOf(closes[i])
                            .multiply(java.math.BigInteger.valueOf(volumes[i])));
            sumVol = sumVol.add(java.math.BigInteger.valueOf(volumes[i]));
        }
        if (sumVol.equals(java.math.BigInteger.ZERO)) {
            return closes[closes.length - 1];
        }
        return sumPriceVol.divide(sumVol).longValue();
    }

    /**
     * Price volatility expressed as the coefficient of variation (stdDev / mean).
     *
     * @param closes close prices, chronological
     * @return CV, or 0.0 if fewer than 2 data points or mean is 0
     */
    public static double computeVolatility(long[] closes) {
        if (closes.length < 2) {
            return 0.0;
        }

        double mean = 0.0;
        for (long c : closes) {
            mean += c;
        }
        mean /= closes.length;

        double variance = 0.0;
        for (long c : closes) {
            double diff = c - mean;
            variance += diff * diff;
        }
        variance /= closes.length;

        double stdDev = Math.sqrt(variance);
        if (mean == 0.0) {
            return 0.0;
        }
        return stdDev / mean;
    }

    /**
     * Signed volume imbalance (buy volume - sell volume) across all candles.
     *
     * <p>A candle is considered "bullish" when close &gt; open and "bearish"
     * when close &lt; open. The full volume of a bullish candle is attributed
     * to buying pressure; the full volume of a bearish candle to selling pressure.
     *
     * @param opens  open prices, chronological
     * @param closes close prices, chronological
     * @param volumes corresponding volumes
     * @return signed imbalance (positive = buy pressure dominates)
     */
    public static long computeVolumeImbalance(long[] opens, long[] closes, long[] volumes) {
        long imbalance = 0;
        for (int i = 0; i < closes.length; i++) {
            if (closes[i] > opens[i]) {
                imbalance += volumes[i];
            } else if (closes[i] < opens[i]) {
                imbalance -= volumes[i];
            }
        }
        return imbalance;
    }
}
