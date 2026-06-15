package com.tradej.core.config;

import com.tradej.core.domain.model.RiskLimits;

public record RiskProfile(
        RiskLimits limits,
        boolean enforceMargin,
        long marginCacheTtlMinutes,
        boolean enforceUnrealizedLoss
) {
    public static RiskProfile defaults() {
        return new RiskProfile(
                new RiskLimits(500_00L, 3, 200_000_00L, 1000, 10),
                true, 5L, true
        );
    }
}
