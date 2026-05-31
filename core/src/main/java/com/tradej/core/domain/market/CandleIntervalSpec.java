package com.tradej.core.domain.market;

/**
 * Canonical candle interval definition shared by historical resampling and live aggregation.
 */
public record CandleIntervalSpec(
        String code,
        long durationMs,
        AggregationMode mode
) {
    public enum AggregationMode {
        SUB_SECOND,
        INTRADAY_SESSION,
        DAILY_SESSION
    }

    public static CandleIntervalSpec parse(String interval) {
        if (interval == null || interval.isBlank()) {
            return new CandleIntervalSpec("1m", 60_000L, AggregationMode.INTRADAY_SESSION);
        }
        return switch (interval.toLowerCase()) {
            case "1s" -> new CandleIntervalSpec("1s", 1_000L, AggregationMode.SUB_SECOND);
            case "1m" -> new CandleIntervalSpec("1m", 60_000L, AggregationMode.INTRADAY_SESSION);
            case "3m" -> new CandleIntervalSpec("3m", 180_000L, AggregationMode.INTRADAY_SESSION);
            case "5m" -> new CandleIntervalSpec("5m", 300_000L, AggregationMode.INTRADAY_SESSION);
            case "15m" -> new CandleIntervalSpec("15m", 900_000L, AggregationMode.INTRADAY_SESSION);
            case "30m" -> new CandleIntervalSpec("30m", 1_800_000L, AggregationMode.INTRADAY_SESSION);
            case "1h" -> new CandleIntervalSpec("1h", 3_600_000L, AggregationMode.INTRADAY_SESSION);
            case "4h" -> new CandleIntervalSpec("4h", 14_400_000L, AggregationMode.INTRADAY_SESSION);
            case "1d" -> new CandleIntervalSpec("1d", 0L, AggregationMode.DAILY_SESSION);
            default -> parseSuffix(interval);
        };
    }

    private static CandleIntervalSpec parseSuffix(String interval) {
        String normalized = interval.toLowerCase();
        if (normalized.endsWith("s")) {
            long seconds = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            return new CandleIntervalSpec(normalized, seconds * 1_000L, AggregationMode.SUB_SECOND);
        }
        if (normalized.endsWith("m")) {
            long minutes = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            return new CandleIntervalSpec(normalized, minutes * 60_000L, AggregationMode.INTRADAY_SESSION);
        }
        if (normalized.endsWith("h")) {
            long hours = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            return new CandleIntervalSpec(normalized, hours * 3_600_000L, AggregationMode.INTRADAY_SESSION);
        }
        if (normalized.endsWith("d")) {
            return new CandleIntervalSpec(normalized, 0L, AggregationMode.DAILY_SESSION);
        }
        return new CandleIntervalSpec("1m", 60_000L, AggregationMode.INTRADAY_SESSION);
    }

    public boolean isOneMinuteOrFiner() {
        return mode == AggregationMode.SUB_SECOND
                || (mode == AggregationMode.INTRADAY_SESSION && durationMs <= 60_000L);
    }
}
