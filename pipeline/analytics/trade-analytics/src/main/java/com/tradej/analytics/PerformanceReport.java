package com.tradej.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Immutable summary of performance statistics for a closed trade set or execution. */
public record PerformanceReport(
        String reportId,
        String executionId,
        int totalTrades,
        int winningTrades,
        int losingTrades,
        BigDecimal totalReturnPct,
        BigDecimal sharpeRatio,
        BigDecimal sortinoRatio,
        BigDecimal maxDrawdownPct,
        BigDecimal winRatePct,
        BigDecimal profitFactor,
        BigDecimal expectancy,
        Map<String, BigDecimal> extraMetrics,
        Instant generatedAt
) {
    public static PerformanceReport empty(String executionId) {
        return new PerformanceReport(
                java.util.UUID.randomUUID().toString(),
                executionId,
                0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(),
                Instant.now()
        );
    }
}
