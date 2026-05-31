package com.tradej.core.domain.market;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * NSE session-aligned candle bucketing (anchor 09:15 IST) shared by historical and live paths.
 */
public final class CandleBucketPolicy {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final LocalTime SESSION_OPEN = LocalTime.of(9, 15);
    public static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);

    private CandleBucketPolicy() {
    }

    public static long bucketStartMs(long barTimeMs, CandleIntervalSpec spec) {
        if (spec.mode() == CandleIntervalSpec.AggregationMode.SUB_SECOND) {
            return (barTimeMs / spec.durationMs()) * spec.durationMs();
        }
        if (spec.mode() == CandleIntervalSpec.AggregationMode.DAILY_SESSION) {
            return sessionOpenMs(tradingDate(barTimeMs));
        }
        long sessionOpenMs = sessionOpenMs(tradingDate(barTimeMs));
        long elapsed = Math.max(0L, barTimeMs - sessionOpenMs);
        long bucketIndex = elapsed / spec.durationMs();
        return sessionOpenMs + bucketIndex * spec.durationMs();
    }

    public static long bucketEndMs(long bucketStartMs, CandleIntervalSpec spec) {
        if (spec.mode() == CandleIntervalSpec.AggregationMode.DAILY_SESSION) {
            LocalDate date = Instant.ofEpochMilli(bucketStartMs).atZone(IST).toLocalDate();
            return date.atTime(SESSION_CLOSE).atZone(IST).toInstant().toEpochMilli();
        }
        if (spec.mode() == CandleIntervalSpec.AggregationMode.SUB_SECOND) {
            return bucketStartMs + spec.durationMs() - 1;
        }
        return bucketStartMs + spec.durationMs() - 1;
    }

    public static LocalDate tradingDate(long barTimeMs) {
        return Instant.ofEpochMilli(barTimeMs).atZone(IST).toLocalDate();
    }

    public static long sessionOpenMs(LocalDate date) {
        return date.atTime(SESSION_OPEN).atZone(IST).toInstant().toEpochMilli();
    }
}
