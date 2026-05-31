package com.tradej.core.domain.market;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class CandleBucketPolicyTest {

    private static final ZoneId IST = CandleBucketPolicy.IST;

    @Test
    void alignsFiveMinuteBucketToSessionOpen() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        long sessionOpen = ZonedDateTime.of(date, LocalTime.of(9, 15), IST).toInstant().toEpochMilli();
        long nineSixteen = ZonedDateTime.of(date, LocalTime.of(9, 16), IST).toInstant().toEpochMilli();
        long nineTwenty = ZonedDateTime.of(date, LocalTime.of(9, 20), IST).toInstant().toEpochMilli();

        CandleIntervalSpec fiveMin = CandleIntervalSpec.parse("5m");
        assertEquals(sessionOpen, CandleBucketPolicy.bucketStartMs(sessionOpen, fiveMin));
        assertEquals(sessionOpen, CandleBucketPolicy.bucketStartMs(nineSixteen, fiveMin));
        assertEquals(sessionOpen + 300_000L, CandleBucketPolicy.bucketStartMs(nineTwenty, fiveMin));
    }

    @Test
    void dailyBucketStartsAtSessionOpen() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        long midday = ZonedDateTime.of(date, LocalTime.of(12, 0), IST).toInstant().toEpochMilli();
        CandleIntervalSpec daily = CandleIntervalSpec.parse("1d");
        assertEquals(CandleBucketPolicy.sessionOpenMs(date), CandleBucketPolicy.bucketStartMs(midday, daily));
    }
}
