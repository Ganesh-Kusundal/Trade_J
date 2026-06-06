package com.tradej.core.service;

import com.tradej.core.domain.model.Order;

import java.time.LocalDate;
import java.util.List;

/**
 * Unified backtest facade shared by CLI, app, terminal, and MCP tools.
 * Framework-independent — no Spring.
 */
public interface BacktestService {
    BacktestResult run(String strategyName, String symbol, LocalDate from, LocalDate to);
    BacktestResult run(BacktestConfig config);
    BacktestStatus status(String runId);
    List<BacktestResult> listResults(int limit);

    record BacktestConfig(
        String strategyName,
        String symbol,
        String interval,
        long fromMs,
        long toMs,
        long spreadBps,
        long volatilitySlippageBps,
        boolean partialFillEnabled,
        double partialFillRatio,
        int minFillSize,
        long maxSlippageBps
    ) {
        public static BacktestConfig simple(String strategyName, String symbol, String interval, long fromMs, long toMs) {
            return new BacktestConfig(strategyName, symbol, interval, fromMs, toMs, 0L, 0L, false, 1.0, 0, 0L);
        }
    }

    record BacktestResult(
        String runId,
        String strategyName,
        String symbol,
        long totalTrades,
        long winningTrades,
        long losingTrades,
        double totalPnlPaisa,
        double maxDrawdownPaisa,
        double sharpeRatio,
        List<Order> orders,
        String status
    ) {}

    record BacktestStatus(
        String runId,
        String state,
        double progressPct,
        String startedAt,
        String completedAt
    ) {}
}
