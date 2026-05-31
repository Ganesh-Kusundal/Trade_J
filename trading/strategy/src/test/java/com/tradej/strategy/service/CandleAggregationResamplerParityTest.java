package com.tradej.strategy.service;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.market.CandleIntervalSpec;
import com.tradej.historical.ingest.resample.CandleResampler;
import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class CandleAggregationResamplerParityTest {

    private static final ZoneId IST = CandleBucketPolicy.IST;

    @Test
    void resamplerAndBucketPolicyAgreeOnFiveMinuteBoundaries() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        List<Candle> oneMinute = new ArrayList<>();
        for (int minute = 0; minute < 10; minute++) {
            long start = ist(date, 9, 15 + minute);
            oneMinute.add(new Candle(
                    "SBIN", "1m", start,
                    CandleBucketPolicy.bucketEndMs(start, CandleIntervalSpec.parse("1m")),
                    100, 110, 90, 105, 10, true));
        }

        List<Candle> resampled = CandleResampler.resample(oneMinute, "5m");
        CandleIntervalSpec fiveMin = CandleIntervalSpec.parse("5m");

        for (Candle bar : resampled) {
            assertEquals(
                    CandleBucketPolicy.bucketStartMs(bar.startTimeMs(), fiveMin),
                    bar.startTimeMs()
            );
        }
    }

    private static long ist(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), IST).toInstant().toEpochMilli();
    }
}
