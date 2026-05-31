package com.tradej.institutional.features;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import com.tradej.institutional.model.InstitutionalScanConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class FeaturePipelineTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void computesMasterScoresForMultipleSymbols() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        List<Candle> stockBars = new ArrayList<>();
        List<Candle> benchmarkBars = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long time = ist(date, 9, 15 + i);
            stockBars.add(candle("AAA", time, 100 + i, 110 + i, 90 + i, 105 + i, 1000 + i));
            stockBars.add(candle("BBB", time, 200 + i, 210 + i, 190 + i, 205 + i, 2000 + i));
            benchmarkBars.add(candle("NIFTY", time, 50 + i, 55 + i, 45 + i, 52 + i, 5000));
        }

        List<FeaturePipeline.BarFeatures> features = FeaturePipeline.computeAllFeatures(
                stockBars, benchmarkBars, InstitutionalScanConfig.baseline().masterScoreWeights());

        assertFalse(features.isEmpty());
        assertTrue(features.stream().anyMatch(bar -> bar.symbol().equals("AAA")));
    }

    private static long ist(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), IST).toInstant().toEpochMilli();
    }

    private static Candle candle(String symbol, long startMs, long open, long high, long low, long close, long volume) {
        return new Candle(symbol, "1m", startMs, startMs + 59_999, open, high, low, close, volume, true);
    }
}
