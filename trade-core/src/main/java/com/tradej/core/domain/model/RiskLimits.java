package com.tradej.core.domain.model;

public record RiskLimits(
        long maxDailyLossPaisa,
        int maxConsecutiveLosses,
        long maxOrderValuePaisa,
        int maxOpenPositions
) {
    public static RiskLimits conservative() {
        return new RiskLimits(50_000L, 3, 500_000L, 3);
    }
}
