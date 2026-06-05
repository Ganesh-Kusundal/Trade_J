package com.tradej.core.domain.model;

public record RiskLimits(
        long maxDailyLossPaisa,
        int maxConsecutiveLosses,
        long maxOrderValuePaisa,
        /** Max absolute net quantity per symbol when flipping (legacy name retained in config). */
        int maxOpenPositionQuantity,
        /** Max distinct symbols with non-zero net position. */
        int maxDistinctOpenPositions
) {
    public static RiskLimits conservative() {
        return new RiskLimits(50_000L, 3, 500_000L, 3, 10);
    }

    /** Backward-compatible factory for tests and legacy callers (distinct limit defaults to 10). */
    public static RiskLimits withOpenPositionQuantity(
            long maxDailyLossPaisa,
            int maxConsecutiveLosses,
            long maxOrderValuePaisa,
            int maxOpenPositionQuantity
    ) {
        return new RiskLimits(
                maxDailyLossPaisa,
                maxConsecutiveLosses,
                maxOrderValuePaisa,
                maxOpenPositionQuantity,
                10);
    }

    /** @deprecated use {@link #maxOpenPositionQuantity()} */
    @Deprecated
    public int maxOpenPositions() {
        return maxOpenPositionQuantity;
    }
}
