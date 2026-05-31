package com.tradej.analytics;

import com.tradej.pipeline.platform.model.PipelineExecution;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Produces performance statistics for a given pipeline execution or trade log.
 *
 * <p>Implementations compute Sharpe, Sortino, Calmar, win rate, profit factor,
 * expectancy, and any other standard performance statistics over a closed
 * set of trades or an equity curve.
 */
public interface PerformanceAnalytics {

    PerformanceReport analyze(PipelineExecution execution);

    PerformanceReport analyzeTrades(java.util.List<TradeRecord> trades);
}
