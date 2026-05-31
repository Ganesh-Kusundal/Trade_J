package com.tradej.broker.dhan.historical;

import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.PriceMath;

import java.util.ArrayList;
import java.util.List;

public final class DhanHistoricalDataMapper {
    public List<Candle> toCandles(DhanJsonResponse payload, Instrument instrument, String interval) {
        DhanJsonResponse open = requiredArray(payload, "open");
        DhanJsonResponse high = requiredArray(payload, "high");
        DhanJsonResponse low = requiredArray(payload, "low");
        DhanJsonResponse close = requiredArray(payload, "close");
        DhanJsonResponse volume = requiredArray(payload, "volume");
        DhanJsonResponse timestamp = payload.has("timestamp") ? requiredArray(payload, "timestamp") : requiredArray(payload, "start_Time");
        int size = timestamp.size();
        if (open.size() != size || high.size() != size || low.size() != size || close.size() != size || volume.size() != size) {
            throw new IllegalStateException("Dhan historical payload arrays are misaligned for " + instrument.canonicalSymbol());
        }
        List<Candle> candles = new ArrayList<>(size);
        long durationMs = intervalDurationMs(interval);
        String normalizedInterval = normalizeInterval(interval);
        for (int i = 0; i < size; i++) {
            long startTimeMs = toEpochMillis(timestamp.get(i));
            candles.add(new Candle(
                    instrument.canonicalSymbol(),
                    normalizedInterval,
                    startTimeMs,
                    startTimeMs + durationMs,
                    PriceMath.toPaisa(open.get(i).asText()),
                    PriceMath.toPaisa(high.get(i).asText()),
                    PriceMath.toPaisa(low.get(i).asText()),
                    PriceMath.toPaisa(close.get(i).asText()),
                    volume.get(i).asLong(),
                    true
            ));
        }
        return candles;
    }

    private DhanJsonResponse requiredArray(DhanJsonResponse payload, String field) {
        DhanJsonResponse value = payload.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("Dhan historical payload missing array field `" + field + "`");
        }
        return value;
    }

    private long toEpochMillis(DhanJsonResponse value) {
        long raw = value.asLong();
        return raw > 100_000_000_000L ? raw : raw * 1000L;
    }

    private String normalizeInterval(String interval) {
        String normalized = interval == null ? "" : interval.trim().toLowerCase();
        return switch (normalized) {
            case "d", "day" -> "1d";
            case "1m", "5m", "15m", "25m", "60m", "1h", "1d" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported candle interval " + interval);
        };
    }

    private long intervalDurationMs(String interval) {
        return switch (normalizeInterval(interval)) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "25m" -> 1_500_000L;
            case "60m", "1h" -> 3_600_000L;
            case "1d" -> 86_400_000L;
            default -> throw new IllegalArgumentException("Unsupported candle interval " + interval);
        };
    }
}
