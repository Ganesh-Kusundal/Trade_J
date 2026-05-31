package com.tradej.historical.ingest.resample;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.market.CandleIntervalSpec;
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
class CandleResamplerTest {

    private static final ZoneId IST = CandleBucketPolicy.IST;

    @Test
    void resamplesOneMinuteToThreeMinuteSessionBuckets() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        long t0 = ist(date, 9, 15);
        long t1 = ist(date, 9, 16);
        long t2 = ist(date, 9, 17);

        List<Candle> oneMinute = List.of(
                candle(t0, 100, 110, 90, 105, 10),
                candle(t1, 105, 115, 100, 110, 20),
                candle(t2, 110, 120, 108, 118, 30)
        );

        List<Candle> resampled = CandleResampler.resample(oneMinute, "3m");

        assertEquals(1, resampled.size());
        assertEquals(t0, resampled.getFirst().startTimeMs());
        assertEquals(100, resampled.getFirst().openPaisa());
        assertEquals(120, resampled.getFirst().highPaisa());
        assertEquals(90, resampled.getFirst().lowPaisa());
        assertEquals(118, resampled.getFirst().closePaisa());
        assertEquals(60, resampled.getFirst().volume());
    }

    @Test
    void resamplesFiveMinuteBarsFromSessionAlignedOneMinuteBars() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        long sessionOpen = ist(date, 9, 15);
        List<Candle> oneMinute = new ArrayList<>();
        for (int minute = 0; minute < 5; minute++) {
            oneMinute.add(candle(sessionOpen + minute * 60_000L, 100 + minute, 110, 90, 105, 10));
        }
        oneMinute.add(candle(sessionOpen + 5 * 60_000L, 200, 210, 190, 205, 15));

        List<Candle> resampled = CandleResampler.resample(oneMinute, "5m");

        assertEquals(2, resampled.size());
        assertEquals(sessionOpen, resampled.get(0).startTimeMs());
        assertEquals(sessionOpen + 300_000L, resampled.get(1).startTimeMs());
    }

    private static long ist(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), IST).toInstant().toEpochMilli();
    }

    private static Candle candle(long startMs, long open, long high, long low, long close, long volume) {
        CandleIntervalSpec spec = CandleIntervalSpec.parse("1m");
        long endMs = CandleBucketPolicy.bucketEndMs(startMs, spec);
        return new Candle("SBIN", "1m", startMs, endMs, open, high, low, close, volume, true);
    }
}
