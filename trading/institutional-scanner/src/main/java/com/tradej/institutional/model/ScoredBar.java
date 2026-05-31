package com.tradej.institutional.model;

import java.time.Instant;

public record ScoredBar(
        String symbol,
        Instant barTime,
        long closePaisa,
        long highPaisa,
        long lowPaisa,
        long openPaisa,
        long volume,
        double rsScore,
        double volumeExpansionScore,
        double trendEfficiencyScore,
        double openingDriveScore,
        double masterScore,
        int rank
) {
    public ScoredBar withRank(int newRank) {
        return new ScoredBar(
                symbol, barTime, closePaisa, highPaisa, lowPaisa, openPaisa, volume,
                rsScore, volumeExpansionScore, trendEfficiencyScore, openingDriveScore, masterScore, newRank
        );
    }
}
