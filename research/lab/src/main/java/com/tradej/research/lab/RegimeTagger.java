package com.tradej.research.lab;

import com.tradej.core.domain.model.Candle;
import java.util.ArrayList;
import java.util.List;

/**
 * Technical analysis component that tag market periods into structural regimes
 * (trend vs range, high vs low volatility) using rolling ATR and ADX.
 */
public final class RegimeTagger {

    public enum MarketRegime {
        STRONG_TREND_BULLISH,
        STRONG_TREND_BEARISH,
        WEAK_TREND,
        HIGH_VOLATILITY_RANGE,
        LOW_VOLATILITY_RANGE
    }

    public record RegimeSnapshot(
        long timeMs,
        MarketRegime regime,
        double atr,
        double atrPercent,
        double adx,
        double plusDI,
        double minusDI
    ) {}

    /**
     * Tags a series of candles. Period is typically 14.
     */
    public static List<RegimeSnapshot> tagRegimes(List<Candle> candles, int period) {
        if (candles == null || candles.size() <= period * 2) {
            return List.of();
        }

        List<RegimeSnapshot> snapshots = new ArrayList<>();
        int n = candles.size();

        double[] tr = new double[n];
        double[] plusDM = new double[n];
        double[] minusDM = new double[n];

        // 1. Calculate TR, +DM, -DM
        for (int i = 1; i < n; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double highDiff = (curr.highPaisa() - prev.highPaisa()) / 100.0;
            double lowDiff = (prev.lowPaisa() - curr.lowPaisa()) / 100.0;

            double tr1 = (curr.highPaisa() - curr.lowPaisa()) / 100.0;
            double tr2 = Math.abs(curr.highPaisa() - prev.closePaisa()) / 100.0;
            double tr3 = Math.abs(curr.lowPaisa() - prev.closePaisa()) / 100.0;
            tr[i] = Math.max(tr1, Math.max(tr2, tr3));

            plusDM[i] = (highDiff > lowDiff && highDiff > 0) ? highDiff : 0.0;
            minusDM[i] = (lowDiff > highDiff && lowDiff > 0) ? lowDiff : 0.0;
        }

        // 2. Wilder's Smoothing
        double smoothedTR = 0.0;
        double smoothedPlusDM = 0.0;
        double smoothedMinusDM = 0.0;

        // Base/initial sums for the first 'period' elements
        for (int i = 1; i <= period; i++) {
            smoothedTR += tr[i];
            smoothedPlusDM += plusDM[i];
            smoothedMinusDM += minusDM[i];
        }

        double[] adx = new double[n];
        double[] plusDI = new double[n];
        double[] minusDI = new double[n];
        double[] atr = new double[n];

        atr[period] = smoothedTR / period;
        plusDI[period] = (smoothedTR > 0) ? 100.0 * (smoothedPlusDM / smoothedTR) : 0.0;
        minusDI[period] = (smoothedTR > 0) ? 100.0 * (smoothedMinusDM / smoothedTR) : 0.0;

        double sumDX = 0.0;

        for (int i = period + 1; i < n; i++) {
            smoothedTR = smoothedTR - (smoothedTR / period) + tr[i];
            smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i];
            smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i];

            atr[i] = smoothedTR / period;

            plusDI[i] = (smoothedTR > 0) ? 100.0 * (smoothedPlusDM / smoothedTR) : 0.0;
            minusDI[i] = (smoothedTR > 0) ? 100.0 * (smoothedMinusDM / smoothedTR) : 0.0;

            double diff = Math.abs(plusDI[i] - minusDI[i]);
            double sum = plusDI[i] + minusDI[i];
            double dx = (sum > 0) ? 100.0 * (diff / sum) : 0.0;

            if (i < period * 2) {
                sumDX += dx;
                if (i == period * 2 - 1) {
                    adx[i] = sumDX / period;
                }
            } else {
                adx[i] = (adx[i - 1] * (period - 1) + dx) / period;

                // We can start tagging from period * 2 onwards
                Candle curr = candles.get(i);
                double close = curr.closePaisa() / 100.0;
                double atrPct = (close > 0) ? (atr[i] / close) * 100.0 : 0.0;

                // Thresholds for regimes:
                // ADX > 25 indicates a strong trend.
                // ADX <= 25 indicates range/accumulation.
                MarketRegime regime;
                if (adx[i] > 25.0) {
                    if (plusDI[i] > minusDI[i]) {
                        regime = MarketRegime.STRONG_TREND_BULLISH;
                    } else {
                        regime = MarketRegime.STRONG_TREND_BEARISH;
                    }
                } else {
                    // For ranging market, we look at the ATR percentage (normalized volatility)
                    // We can use a 1.5% daily volatility (adjusted for bar timeframes if necessary) as high vs low.
                    // Or relative threshold. Let's use 1.0% ATR percentage as a threshold.
                    if (atrPct > 1.0) {
                        regime = MarketRegime.HIGH_VOLATILITY_RANGE;
                    } else {
                        regime = MarketRegime.LOW_VOLATILITY_RANGE;
                    }
                }

                snapshots.add(new RegimeSnapshot(
                    curr.endTimeMs(),
                    regime,
                    atr[i],
                    atrPct,
                    adx[i],
                    plusDI[i],
                    minusDI[i]
                ));
            }
        }

        return snapshots;
    }
}
