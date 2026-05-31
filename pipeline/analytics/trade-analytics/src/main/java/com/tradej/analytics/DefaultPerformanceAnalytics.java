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

    @Override
    public PerformanceReport analyzeTrades(List<TradeRecord> trades) {
        if (trades == null || trades.isEmpty()) {
            return PerformanceReport.empty(UUID.randomUUID().toString());
        }

        int totalTrades = trades.size();
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal totalReturn = BigDecimal.ZERO;
        BigDecimal totalPositiveReturn = BigDecimal.ZERO;
        BigDecimal totalNegativeReturn = BigDecimal.ZERO;
        List<BigDecimal> returns = new ArrayList<>();

        for (TradeRecord trade : trades) {
            BigDecimal pnl = trade.pnl();
            BigDecimal retPct = trade.returnPct();

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                totalPositiveReturn = totalPositiveReturn.add(pnl);
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                losingTrades++;
                totalNegativeReturn = totalNegativeReturn.add(pnl.abs());
            }

            totalReturn = totalReturn.add(pnl);
            if (retPct != null) {
                returns.add(retPct);
            }
        }

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

        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            BigDecimal diff = r.subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(returns.size()), MC);

        BigDecimal stdDev = sqrt(variance);
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return mean.multiply(sqrt252).divide(stdDev, MC);
    }

    private BigDecimal calculateSortinoRatio(List<BigDecimal> returns) {
        if (returns.isEmpty()) return BigDecimal.ZERO;

        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), MC);

        BigDecimal downsideVariance = BigDecimal.ZERO;
        int downsideCount = 0;
        for (BigDecimal r : returns) {
            if (r.compareTo(BigDecimal.ZERO) < 0) {
                downsideVariance = downsideVariance.add(r.multiply(r));
                downsideCount++;
            }
        }

        if (downsideCount == 0) return mean.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(Double.MAX_VALUE)
                : BigDecimal.ZERO;

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