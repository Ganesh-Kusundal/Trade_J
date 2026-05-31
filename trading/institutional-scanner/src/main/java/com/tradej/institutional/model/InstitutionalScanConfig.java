package com.tradej.institutional.model;

import java.util.Map;

public record InstitutionalScanConfig(
        int nStocks,
        int topN,
        int maxPerSector,
        String defaultScanTime,
        Map<String, Double> masterScoreWeights
) {
    public static InstitutionalScanConfig baseline() {
        return new InstitutionalScanConfig(
                200,
                5,
                2,
                "09:45:00",
                Map.of(
                        "rs", 0.25,
                        "volume", 0.20,
                        "opening", 0.15,
                        "sector", 0.10,
                        "trend", 0.10,
                        "orderflow", 0.10,
                        "volatility", 0.05,
                        "liquidity", 0.05
                )
        );
    }
}
