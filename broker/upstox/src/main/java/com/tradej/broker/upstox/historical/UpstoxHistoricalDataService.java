package com.tradej.broker.upstox.historical;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UpstoxHistoricalDataService {
    private final UpstoxHistoricalDataRestClient restClient;
    private final UpstoxInstrumentResolver instrumentResolver;
    private final UpstoxHistoricalCandleMapper mapper = new UpstoxHistoricalCandleMapper();

    public UpstoxHistoricalDataService(
            UpstoxHistoricalDataRestClient restClient,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this.restClient = restClient;
        this.instrumentResolver = instrumentResolver;
    }

    public List<Candle> fetchCandles(CandleHistoryRequest request) {
        LocalDate toDate = request.toDate() == null ? LocalDate.now() : request.toDate();
        LocalDate fromDate = request.fromDate() == null ? toDate.minusDays(89) : request.fromDate();
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must be on/after fromDate");
        }
        String instrumentKey = instrumentResolver.requireInstrumentKey(request.instrument());
        String interval = normalizeInterval(request.interval());
        long intervalMs = intervalDurationMs(interval);
        Instrument instrument = instrumentResolver.requireDefinition(request.instrument());
        int maxDays = isDailyInterval(interval)
                ? UpstoxHistoricalDataRestClient.DAILY_MAX_DAYS
                : UpstoxHistoricalDataRestClient.INTRADAY_MAX_DAYS;
        List<Candle> merged = new ArrayList<>();
        for (DateWindow window : splitDateWindows(fromDate, toDate, maxDays)) {
            JsonNode payload = restClient.getHistoricalCandles(
                    instrumentKey, interval, window.fromDate(), window.toDate());
            merged.addAll(mapper.toCandles(payload, instrument, interval, intervalMs));
        }
        return dedupeAndSort(merged);
    }

    static List<DateWindow> splitDateWindows(LocalDate fromDate, LocalDate toDate, int maxDaysPerRequest) {
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            LocalDate windowEnd = cursor.plusDays(maxDaysPerRequest - 1L);
            if (windowEnd.isAfter(toDate)) {
                windowEnd = toDate;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1L);
        }
        return windows;
    }

    static List<Candle> dedupeAndSort(List<Candle> candles) {
        Map<String, Candle> unique = new LinkedHashMap<>();
        for (Candle candle : candles) {
            unique.putIfAbsent(candle.startTimeMs() + "|" + candle.endTimeMs(), candle);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingLong(Candle::startTimeMs))
                .toList();
    }

    private static boolean isDailyInterval(String interval) {
        return "1d".equals(interval) || "day".equalsIgnoreCase(interval)
                || "week".equalsIgnoreCase(interval) || "month".equalsIgnoreCase(interval);
    }

    private static String normalizeInterval(String interval) {
        if (interval == null) {
            return "day";
        }
        String normalized = switch (interval.trim().toLowerCase()) {
            case "1d", "d", "day" -> "day";
            case "1m", "1minute" -> "1minute";
            case "30m", "30minute" -> "30minute";
            case "week" -> "week";
            case "month" -> "month";
            case "5m", "5minute", "15m", "15minute", "1h", "60m", "60minute", "1hour" ->
                    throw new IllegalArgumentException(
                            "Upstox V3 API does not support '" + interval + "' interval. "
                                    + "Supported intraday intervals: 1minute, 30minute. "
                                    + "Use Dhan or ICICI for 5m/15m/60m candles.");
            default -> interval.trim().toLowerCase();
        };
        validateUpstoxInterval(normalized);
        return normalized;
    }

    static void validateUpstoxInterval(String interval) {
        if (!isSupportedUpstoxInterval(interval)) {
            throw new IllegalArgumentException(
                    "Upstox historical interval '" + interval + "' is not supported. "
                            + "Use one of: 1minute, 30minute, day, week, month");
        }
    }

    static boolean isSupportedUpstoxInterval(String interval) {
        return switch (interval) {
            case "1minute", "30minute", "day", "week", "month" -> true;
            default -> false;
        };
    }

    private static long intervalDurationMs(String interval) {
        return switch (interval) {
            case "1minute" -> 60_000L;
            case "5minute" -> 300_000L;
            case "15minute" -> 900_000L;
            case "30minute" -> 1_800_000L;
            case "day", "week", "month" -> 86_400_000L;
            default -> 86_400_000L;
        };
    }

    record DateWindow(LocalDate fromDate, LocalDate toDate) {
    }
}
