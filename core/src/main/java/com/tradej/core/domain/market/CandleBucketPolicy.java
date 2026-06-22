package com.tradej.core.domain.market;

import com.tradej.core.domain.time.ExchangeCalendar;
import com.tradej.core.domain.time.MarketTime;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * NSE session-aligned candle bucketing (anchor 09:15 IST) shared by historical and live paths.
 */
public final class CandleBucketPolicy {

    public static final java.time.ZoneId IST = MarketTime.IST;
    public static final LocalTime SESSION_OPEN = ExchangeCalendar.sessionHours(ExchangeSegment.NSE_EQ).open();
    public static final LocalTime SESSION_CLOSE = ExchangeCalendar.sessionHours(ExchangeSegment.NSE_EQ).close();

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
            return MarketTime.atTimeMs(date, SESSION_CLOSE);
        }
        if (spec.mode() == CandleIntervalSpec.AggregationMode.SUB_SECOND) {
            return bucketStartMs + spec.durationMs() - 1;
        }
        return bucketStartMs + spec.durationMs() - 1;
    }

    public static LocalDate tradingDate(long barTimeMs) {
        return MarketTime.tradingDate(barTimeMs);
    }

    public static long sessionOpenMs(LocalDate date) {
        return MarketTime.atTimeMs(date, SESSION_OPEN);
    }
}
