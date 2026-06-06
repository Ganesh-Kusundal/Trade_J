package com.tradej.core.domain.model;

import java.time.LocalDate;

/**
 * Canonical backtest result record used across all modules.
 * All module-specific backtest result types should provide a
 * {@code toCanonical()} adapter that converts to this record.
 *
 * @param totalPnlPaisa    net realized P&L in paisa
 * @param totalTrades      total number of completed round-trip trades
 * @param winningTrades    number of profitable trades
 * @param losingTrades     number of losing trades
 * @param maxDrawdownPaisa maximum peak-to-trough drawdown in paisa
 * @param winRate          fraction of winning trades (0.0–1.0)
 * @param sharpeRatio      annualized risk-adjusted return estimate
 * @param candleCount      number of candles consumed by the backtest
 * @param fromDate         first candle date (inclusive)
 * @param toDate           last candle date (inclusive)
 * @param strategyName     name of the strategy that was run
 */
public record BacktestResult(
        long totalPnlPaisa,
        int totalTrades,
        int winningTrades,
        int losingTrades,
        long maxDrawdownPaisa,
        double winRate,
        double sharpeRatio,
        int candleCount,
        LocalDate fromDate,
        LocalDate toDate,
        String strategyName
) {
    /**
     * Profit factor: ratio of winning trades to losing trades.
     * Returns {@link Double#MAX_VALUE} when there are no losing trades.
     * Returns 0.0 when there are no trades at all.
     */
    public double profitFactor() {
        if (totalTrades == 0) return 0.0;
        if (losingTrades == 0) return Double.MAX_VALUE;
        return (double) winningTrades / losingTrades;
    }
}
