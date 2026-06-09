package com.tradej.broker.api.model;

import java.util.Set;

/**
 * Declares broker historical data API capabilities for interval support and fetch limits.
 */
public record HistoricalDataCapabilities(
        Set<String> supportedIntervals,
        int maxIntradayDaysPerRequest,
        int maxDailyDaysPerRequest,
        boolean supportsSecondHistorical,
        int maxRowsPerRequest
) {
    public static final HistoricalDataCapabilities EMPTY = new HistoricalDataCapabilities(
            java.util.Set.of(), 0, 0, false, 0
    );

    public static HistoricalDataCapabilities iciciDefaults() {
        return new HistoricalDataCapabilities(
                Set.of("1s", "1second", "1m", "1minute", "minute", "5m", "5minute", "30m", "30minute", "1d", "1day", "day"),
                1,
                365,
                true,
                1000
        );
    }

    public static HistoricalDataCapabilities dhanDefaults() {
        return new HistoricalDataCapabilities(
                Set.of("1m", "5m", "15m", "25m", "60m", "1h", "1d", "day"),
                90,
                3650,
                false,
                Integer.MAX_VALUE
        );
    }

    public static HistoricalDataCapabilities upstoxDefaults() {
        return new HistoricalDataCapabilities(
                Set.of("1m", "1minute", "30m", "30minute", "1d", "day", "week", "month"),
                90,
                365,
                false,
                Integer.MAX_VALUE
        );
    }
}
