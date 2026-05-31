package com.tradej.broker.icici.http;

/**
 * Maps Trade_J interval aliases to Breeze v1 historical API interval values.
 * Matches {@code breeze_connect} which converts {@code 1minute}→{@code minute}, {@code 1day}→{@code day}.
 */
public final class BreezeHistoricalIntervals {

    private BreezeHistoricalIntervals() {
    }

    /** v2 {@code get_historical_data_v2} interval values ({@code 1second}, {@code 1minute}, …). */
    public static String toV2ApiInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return "1minute";
        }
        return switch (interval.toLowerCase()) {
            case "1s", "1second", "second" -> "1second";
            case "1m", "1minute", "minute" -> "1minute";
            case "5m", "5minute" -> "5minute";
            case "30m", "30minute" -> "30minute";
            case "1d", "1day", "day" -> "1day";
            default -> interval;
        };
    }

    public static String toApiInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return "minute";
        }
        return switch (interval.toLowerCase()) {
            case "1s", "1second", "second" -> "1second";
            case "1m", "1minute", "minute" -> "minute";
            case "5m", "5minute" -> "5minute";
            case "30m", "30minute" -> "30minute";
            case "1d", "1day", "day" -> "day";
            default -> interval;
        };
    }

    public static boolean isDaily(String apiInterval) {
        return "day".equalsIgnoreCase(apiInterval);
    }

    public static boolean isSecond(String apiInterval) {
        return "1second".equalsIgnoreCase(apiInterval);
    }

    public static long intervalDurationMs(String apiInterval) {
        return switch (apiInterval.toLowerCase()) {
            case "1second" -> 1_000L;
            case "minute" -> 60_000L;
            case "5minute" -> 300_000L;
            case "30minute" -> 1_800_000L;
            case "day" -> 86_400_000L;
            default -> 60_000L;
        };
    }
}
