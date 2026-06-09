package com.tradej.analytics;

import com.tradej.pipeline.platform.model.PipelineExecution;
import com.tradej.pipeline.platform.model.PipelineExecutionStatus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class DefaultPerformanceAnalytics implements PerformanceAnalytics {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal sqrt252 = BigDecimal.valueOf(Math.sqrt(252));

    @Override
    public PerformanceReport analyze(PipelineExecution execution) {
        Objects.requireNonNull(execution, "execution");
        if (execution.status() != PipelineExecutionStatus.COMPLETED &&
            execution.status() != PipelineExecutionStatus.FAILED) {
            return PerformanceReport.empty(execution.executionId().toString());
        }
        return PerformanceReport.empty(execution.executionId().toString());
    }

    record TradeAccumulator(
            int totalTrades,
            int winningTrades,
            int losingTrades,
            BigDecimal totalReturn,
            BigDecimal totalPositiveReturn,
            BigDecimal totalNegativeReturn,
            List<BigDecimal> returns
    ) {
        static TradeAccumulator from(TradeRecord trade) {
            BigDecimal pnl = trade.pnl();
            BigDecimal retPct = trade.returnPct();
            return new TradeAccumulator(
                    1,
                    pnl.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0,
                    pnl.compareTo(BigDecimal.ZERO) < 0 ? 1 : 0,
                    pnl,
                    pnl.compareTo(BigDecimal.ZERO) > 0 ? pnl : BigDecimal.ZERO,
                    pnl.compareTo(BigDecimal.ZERO) < 0 ? pnl.abs() : BigDecimal.ZERO,
                    retPct != null ? List.of(retPct) : List.of()
            );
        }

        TradeAccumulator merge(TradeAccumulator other) {
            List<BigDecimal> mergedReturns = new ArrayList<>(this.returns);
            mergedReturns.addAll(other.returns);
            return new TradeAccumulator(
                    this.totalTrades + other.totalTrades,
                    this.winningTrades + other.winningTrades,
                    this.losingTrades + other.losingTrades,
                    this.totalReturn.add(other.totalReturn),
                    this.totalPositiveReturn.add(other.totalPositiveReturn),
                    this.totalNegativeReturn.add(other.totalNegativeReturn),
                    mergedReturns
            );
        }
    }

    @Override
    public PerformanceReport analyzeTrades(List<TradeRecord> trades) {
        if (trades == null || trades.isEmpty()) {
            return PerformanceReport.empty(UUID.randomUUID().toString());
        }

        TradeAccumulator acc = trades.stream()
                .map(TradeAccumulator::from)
                .reduce(TradeAccumulator::merge)
                .orElseThrow();

        int totalTrades = acc.totalTrades();
        int winningTrades = acc.winningTrades();
        int losingTrades = acc.losingTrades();
        BigDecimal totalReturn = acc.totalReturn();
        BigDecimal totalPositiveReturn = acc.totalPositiveReturn();
        BigDecimal totalNegativeReturn = acc.totalNegativeReturn();
        List<BigDecimal> returns = acc.returns();

        BigDecimal winRatePct = totalTrades > 0
                ? HUNDRED.multiply(BigDecimal.valueOf(winningTrades)).divide(BigDecimal.valueOf(totalTrades), MC)
                : BigDecimal.ZERO;

        BigDecimal profitFactor = totalNegativeReturn.compareTo(BigDecimal.ZERO) > 0
                ? totalPositiveReturn.divide(totalNegativeReturn, MC)
                : totalPositiveReturn.compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(Double.MAX_VALUE)
                        : BigDecimal.ONE;

        BigDecimal avgReturn = returns.isEmpty()
                ? BigDecimal.ZERO
                : returns.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(returns.size()), MC);

        BigDecimal expectancy = BigDecimal.valueOf(winningTrades)
                .multiply(avgReturn)
                .divide(BigDecimal.valueOf(totalTrades), MC);

        BigDecimal sharpeRatio = calculateSharpeRatio(returns);
        BigDecimal sortinoRatio = calculateSortinoRatio(returns);
        BigDecimal maxDrawdownPct = calculateMaxDrawdown(returns);
        BigDecimal calmarRatio = maxDrawdownPct.abs().compareTo(BigDecimal.ZERO) > 0
                ? avgReturn.multiply(BigDecimal.valueOf(252)).divide(maxDrawdownPct.abs(), MC)
                : BigDecimal.ZERO;

        Map<String, BigDecimal> extraMetrics = new LinkedHashMap<>();
        extraMetrics.put("totalReturn", totalReturn);
        extraMetrics.put("winRate", winRatePct);
        extraMetrics.put("profitFactor", profitFactor);
        extraMetrics.put("avgReturn", avgReturn);
        extraMetrics.put("calmarRatio", calmarRatio);
        extraMetrics.put("totalWinningTrades", BigDecimal.valueOf(winningTrades));
        extraMetrics.put("totalLosingTrades", BigDecimal.valueOf(losingTrades));

        return new PerformanceReport(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                totalTrades,
                winningTrades,
                losingTrades,
                totalReturn,
                sharpeRatio,
                sortinoRatio,
                maxDrawdownPct,
                winRatePct,
                profitFactor,
                expectancy,
                extraMetrics,
                Instant.now()
        );
    }

    private BigDecimal calculateSharpeRatio(List<BigDecimal> returns) {
        if (returns.isEmpty()) return BigDecimal.ZERO;

        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), MC);

        BigDecimal variance = returns.stream()
                .map(r -> r.subtract(mean))
                .map(diff -> diff.multiply(diff))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), MC);

        BigDecimal stdDev = sqrt(variance);
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return mean.multiply(sqrt252).divide(stdDev, MC);
    }

    private BigDecimal calculateSortinoRatio(List<BigDecimal> returns) {
        if (returns.isEmpty()) return BigDecimal.ZERO;

        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), MC);

        BigDecimal downsideVariance = returns.stream()
                .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
                .map(r -> r.multiply(r))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (downsideVariance.compareTo(BigDecimal.ZERO) == 0) {
            return mean.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(Double.MAX_VALUE)
                    : BigDecimal.ZERO;
        }

        downsideVariance = downsideVariance.divide(BigDecimal.valueOf(returns.size()), MC);
        BigDecimal downsideDev = sqrt(downsideVariance);
        if (downsideDev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return mean.multiply(sqrt252).divide(downsideDev, MC);
    }

    private BigDecimal calculateMaxDrawdown(List<BigDecimal> returns) {
        if (returns.isEmpty()) return BigDecimal.ZERO;

        BigDecimal cumulative = BigDecimal.valueOf(100);
        BigDecimal peak = cumulative;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (BigDecimal r : returns) {
            cumulative = cumulative.multiply(BigDecimal.ONE.add(r.divide(BigDecimal.valueOf(100), MC)));
            if (cumulative.compareTo(peak) > 0) {
                peak = cumulative;
            }
            BigDecimal drawdown = peak.subtract(cumulative);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown.compareTo(BigDecimal.ZERO) > 0
                ? maxDrawdown.divide(peak, MC).multiply(HUNDRED)
                : BigDecimal.ZERO;
    }

    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
    }
}