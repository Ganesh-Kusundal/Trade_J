package com.tradej.institutional.ranking;

import com.tradej.institutional.features.FeaturePipeline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class RankingEngineTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void appliesSectorCapWhenRanking() {
        Instant time = LocalDate.of(2026, 5, 22).atTime(LocalTime.of(9, 45)).atZone(IST).toInstant();
        List<FeaturePipeline.BarFeatures> group = List.of(
                feature("AAA", time, 1.0, "Tech"),
                feature("BBB", time, 0.9, "Tech"),
                feature("CCC", time, 0.8, "Bank")
        );
        RankingEngine engine = new RankingEngine(1, Map.of(
                "AAA", "Tech", "BBB", "Tech", "CCC", "Bank"
        ));
        List<FeaturePipeline.BarFeatures> ranked = engine.rankStocks(group, 3);
        long techSymbols = ranked.stream().filter(bar -> "AAA".equals(bar.symbol()) || "BBB".equals(bar.symbol())).count();
        assertTrue(techSymbols <= 1);
        assertEquals(2, ranked.size());
    }

    private static FeaturePipeline.BarFeatures feature(
            String symbol, Instant time, double masterScore, String ignored
    ) {
        return new FeaturePipeline.BarFeatures(
                symbol, time, 1000, 1100, 900, 950, 100,
                0.1, 0.2, 0.3, 0.4, 0.0, 0.0, 0.0, 0.0, masterScore
        );
    }
}
