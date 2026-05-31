package com.tradej.institutional.sector;

import com.tradej.institutional.features.FeaturePipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SectorRankingEngine {

    private SectorRankingEngine() {
    }

    public static Map<String, Double> computeSectorMomentum(
            List<FeaturePipeline.BarFeatures> bars,
            Map<String, String> industryMap
    ) {
        Map<String, Double> sectorReturns = new HashMap<>();
        Map<String, Integer> sectorCounts = new HashMap<>();
        for (FeaturePipeline.BarFeatures bar : bars) {
            String sector = industryMap.getOrDefault(bar.symbol(), "Unknown");
            sectorReturns.merge(sector, bar.openingDriveScore(), Double::sum);
            sectorCounts.merge(sector, 1, Integer::sum);
        }
        Map<String, Double> momentum = new HashMap<>();
        for (Map.Entry<String, Double> entry : sectorReturns.entrySet()) {
            int count = sectorCounts.getOrDefault(entry.getKey(), 1);
            momentum.put(entry.getKey(), entry.getValue() / count);
        }
        return momentum;
    }

    public static List<FeaturePipeline.BarFeatures> applySectorPenalty(
            List<FeaturePipeline.BarFeatures> bars,
            Map<String, String> industryMap,
            Map<String, Double> sectorMomentum
    ) {
        if (sectorMomentum.isEmpty()) {
            return bars;
        }
        double avgMomentum = sectorMomentum.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return bars.stream()
                .map(bar -> {
                    String sector = industryMap.getOrDefault(bar.symbol(), "Unknown");
                    double sectorScore = sectorMomentum.getOrDefault(sector, avgMomentum);
                    double penalty = sectorScore < avgMomentum ? -0.1 : 0.0;
                    return new FeaturePipeline.BarFeatures(
                            bar.symbol(), bar.barTime(), bar.closePaisa(), bar.highPaisa(), bar.lowPaisa(),
                            bar.openPaisa(), bar.volume(), bar.rsScore(), bar.volumeExpansionScore(),
                            bar.trendEfficiencyScore(), bar.openingDriveScore(), bar.bidAskImbalanceScore(),
                            bar.deltaExpansionScore(), bar.volatilityExpansionScore(), bar.liquidityScore(),
                            bar.masterScore() + penalty
                    );
                })
                .toList();
    }
}
