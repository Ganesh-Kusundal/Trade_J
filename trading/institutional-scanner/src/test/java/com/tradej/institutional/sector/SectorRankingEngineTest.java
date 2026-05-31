package com.tradej.institutional.sector;

import com.tradej.institutional.features.FeaturePipeline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class SectorRankingEngineTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void penalizesWeakSectors() {
        Instant time = LocalDate.of(2026, 5, 22).atTime(LocalTime.of(9, 45)).atZone(IST).toInstant();
        List<FeaturePipeline.BarFeatures> bars = List.of(
                feature("AAA", time, 0.5),
                feature("BBB", time, 0.1)
        );
        Map<String, String> industries = Map.of("AAA", "Tech", "BBB", "Bank");
        Map<String, Double> momentum = SectorRankingEngine.computeSectorMomentum(bars, industries);
        assertFalse(momentum.isEmpty());

        List<FeaturePipeline.BarFeatures> adjusted = SectorRankingEngine.applySectorPenalty(bars, industries, momentum);
        double weakSectorScore = adjusted.stream()
                .filter(bar -> "BBB".equals(bar.symbol()))
                .mapToDouble(FeaturePipeline.BarFeatures::masterScore)
                .findFirst()
                .orElseThrow();
        double strongSectorScore = adjusted.stream()
                .filter(bar -> "AAA".equals(bar.symbol()))
                .mapToDouble(FeaturePipeline.BarFeatures::masterScore)
                .findFirst()
                .orElseThrow();
        assertTrue(weakSectorScore < strongSectorScore, "Weak sector should receive penalty");
    }

    private static FeaturePipeline.BarFeatures feature(String symbol, Instant time, double openingDrive) {
        return new FeaturePipeline.BarFeatures(
                symbol, time, 1000, 1100, 900, 950, 100,
                0.0, 0.0, 0.0, openingDrive, 0.0, 0.0, 0.0, 0.0, 0.5
        );
    }
}
