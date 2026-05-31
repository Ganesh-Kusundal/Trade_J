package com.tradej.core.domain.model;

/**
 * Computed feature vector for a symbol at a point in time.
 * All prices are in paisa (1/100th of a rupee).
 *
 * @param symbol             trading symbol
 * @param interval           candle interval (e.g. "5m", "1d")
 * @param timestampMs        epoch millis of the feature computation
 * @param rsi                Relative Strength Index (0–100)
 * @param ema5Paisa          5-period exponential moving average
 * @param ema9Paisa          9-period exponential moving average
 * @param ema21Paisa         21-period exponential moving average
 * @param sma20Paisa         20-period simple moving average
 * @param sma50Paisa         50-period simple moving average
 * @param vwapPaisa          Volume Weighted Average Price
 * @param volatility         price volatility (coefficient of variation, close prices)
 * @param volumeImbalance    signed volume imbalance (buy - sell)
 * @param bidAskSpreadPaisa  current bid-ask spread from market depth
 */
public record FeatureVector(
        String symbol,
        String interval,
        long timestampMs,
        double rsi,
        long ema5Paisa,
        long ema9Paisa,
        long ema21Paisa,
        long sma20Paisa,
        long sma50Paisa,
        long vwapPaisa,
        double volatility,
        long volumeImbalance,
        long bidAskSpreadPaisa
) {
}
