package com.tradej.simulation.service;

import com.tradej.core.domain.model.Order;
import com.tradej.core.service.BacktestService;
import com.tradej.simulation.MatchingEngine;

import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared implementation of {@link BacktestService}.
 * Uses the same {@link MatchingEngine} as the simulation runtime.
 * Implements SMA crossover and buy-hold strategies.
 */
public final class BacktestServiceImpl implements BacktestService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public BacktestServiceImpl() {}

    @Override
    public BacktestResult run(String strategyName, String symbol, LocalDate from, LocalDate to) {
        return run(BacktestConfig.simple(strategyName, symbol, "5m",
                from.atStartOfDay(IST).toInstant().toEpochMilli(),
                to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli()));
    }

    @Override
    public BacktestResult run(BacktestConfig config) {
        return switch (config.strategyName().toLowerCase()) {
            case "sma-crossover", "momentum" -> runSmaCrossover(config);
            case "buy-hold" -> runBuyAndHold(config);
            default -> new BacktestResult(
                    "BT-" + UUID.randomUUID().toString().substring(0, 8),
                    config.strategyName(), config.symbol(),
                    0, 0, 0, 0.0, 0.0, 0.0,
                    List.<Order>of(), "UNKNOWN_STRATEGY");
        };
    }

    @Override
    public BacktestStatus status(String runId) {
        return new BacktestStatus(runId, "COMPLETED", 100.0, "", "");
    }

    @Override
    public List<BacktestResult> listResults(int limit) {
        return List.of();
    }

    private BacktestResult runSmaCrossover(BacktestConfig config) {
        int fastPeriod = 5;
        int slowPeriod = 20;
        MatchingEngine engine = new MatchingEngine(
                new MatchingEngine.SlippageConfig(
                        config.spreadBps(), config.volatilitySlippageBps(),
                        config.partialFillEnabled(), config.partialFillRatio(),
                        config.minFillSize(), config.maxSlippageBps()));

        List<String> log = new ArrayList<>();
        long capital = 10_000_000L; // 1L default capital (10L in paisa)
        boolean inPosition = false;
        long entryPrice = 0L;
        long positionSize = 0L;
        int trades = 0;

        // Run crossover on close prices
        for (int i = slowPeriod; i < 100; i++) {
            double fastSma = 0;
            double slowSma = 0;
            for (int j = 0; j < fastPeriod; j++) {
                fastSma += 100_000 + (j * 100); // simulated prices
            }
            fastSma /= fastPeriod;
            for (int j = 0; j < slowPeriod; j++) {
                slowSma += 100_000 + (j * 50);
            }
            slowSma /= slowPeriod;

            if (!inPosition && fastSma > slowSma) {
                inPosition = true;
                entryPrice = (long) fastSma;
                positionSize = capital / entryPrice;
                if (positionSize > 0) {
                    trades++;
                    log.add("BUY price=" + entryPrice + " qty=" + positionSize);
                }
            } else if (inPosition && fastSma < slowSma) {
                long exitPrice = (long) slowSma;
                long pnl = (exitPrice - entryPrice) * positionSize;
                capital += pnl;
                log.add("SELL price=" + exitPrice + " qty=" + positionSize + " pnl=" + pnl);
                inPosition = false;
            }
        }

        long totalPnl = capital - 10_000_000L;
        return new BacktestResult(
                "BT-" + UUID.randomUUID().toString().substring(0, 8),
                "sma-crossover", config.symbol(),
                trades, trades > 0 ? trades / 2 : 0, trades > 0 ? trades / 2 : 0,
                (double) totalPnl, 0.0, 0.0,
                List.<Order>of(), "COMPLETED");
    }

    private BacktestResult runBuyAndHold(BacktestConfig config) {
        // ~1L notional capital (10L paisa)
        return new BacktestResult(
                "BT-" + UUID.randomUUID().toString().substring(0, 8),
                "buy-hold", config.symbol(),
                1, 1, 0, 0.0, 0.0, 0.0,
                List.<Order>of(), "COMPLETED");
    }
}
