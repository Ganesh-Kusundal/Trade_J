package com.tradej.core.domain.time;

import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exchange-specific session calendar that provides open, close, and pre-open
 * times for each {@link ExchangeSegment}.
 *
 * <p>Replaces the previous binary MCX-vs-rest logic in
 * {@link com.tradej.core.domain.value.SessionSchedule} with a proper
 * per-segment configuration.
 */
public final class ExchangeCalendar {

    /**
     * Session hours for a single exchange segment.
     *
     * @param open    the time the regular trading session opens
     * @param close   the time the regular trading session closes
     * @param preOpen the time the pre-open session begins
     */
    public record SessionHours(LocalTime open, LocalTime close, LocalTime preOpen) {
    }

    private static final Map<String, SessionHours> CALENDARS = new ConcurrentHashMap<>();

    static {
        CALENDARS.put("NSE_EQ", new SessionHours(LocalTime.of(9, 15), LocalTime.of(15, 30), LocalTime.of(9, 0)));
        CALENDARS.put("NSE_FNO", new SessionHours(LocalTime.of(9, 15), LocalTime.of(15, 30), LocalTime.of(9, 0)));
        CALENDARS.put("BSE_EQ", new SessionHours(LocalTime.of(9, 15), LocalTime.of(15, 30), LocalTime.of(9, 0)));
        CALENDARS.put("MCX_COMM", new SessionHours(LocalTime.of(9, 0), LocalTime.of(23, 30), LocalTime.of(8, 45)));
        CALENDARS.put("NSE_CURRENCY", new SessionHours(LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(8, 45)));
        CALENDARS.put("BSE_CURRENCY", new SessionHours(LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(8, 45)));
        CALENDARS.put("IDX_I", new SessionHours(LocalTime.of(9, 15), LocalTime.of(15, 30), LocalTime.of(9, 0)));
    }

    private ExchangeCalendar() {
    }

    /**
     * Returns the session hours for the given exchange segment.
     * Falls back to NSE_EQ hours if the segment is not explicitly registered.
     */
    public static SessionHours sessionHours(ExchangeSegment segment) {
        return CALENDARS.getOrDefault(segment.name(), CALENDARS.get("NSE_EQ"));
    }

    /**
     * Returns {@code true} if the market for the given segment is open at the
     * specified time. The check is {@code open <= time < close}.
     */
    public static boolean isMarketOpen(ExchangeSegment segment, LocalTime time) {
        SessionHours hours = sessionHours(segment);
        return !time.isBefore(hours.open()) && time.isBefore(hours.close());
    }

    /**
     * Register or override session hours for a given exchange segment at runtime.
     */
    public static void register(ExchangeSegment segment, SessionHours hours) {
        CALENDARS.put(segment.name(), hours);
    }
}
