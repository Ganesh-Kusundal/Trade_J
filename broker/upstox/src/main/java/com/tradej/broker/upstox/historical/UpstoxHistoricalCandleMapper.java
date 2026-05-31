package com.tradej.broker.upstox.historical;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.PriceMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class UpstoxHistoricalCandleMapper {
    public List<Candle> toCandles(JsonNode response, Instrument instrument, String interval, long intervalMs) {
        JsonNode candlesNode = response.path("data").path("candles");
        if (!candlesNode.isArray()) {
            return List.of();
        }
        List<Candle> candles = new ArrayList<>(candlesNode.size());
        for (JsonNode row : candlesNode) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }
            long startTimeMs = parseEpochMs(row.get(0));
            long open = PriceMath.toPaisa(String.valueOf(row.get(1).asDouble()));
            long high = PriceMath.toPaisa(String.valueOf(row.get(2).asDouble()));
            long low = PriceMath.toPaisa(String.valueOf(row.get(3).asDouble()));
            long close = PriceMath.toPaisa(String.valueOf(row.get(4).asDouble()));
            long volume = row.get(5).asLong();
            candles.add(new Candle(
                    instrument.canonicalSymbol(),
                    interval,
                    startTimeMs,
                    startTimeMs + intervalMs,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    true
            ));
        }
        candles.sort(Comparator.comparingLong(Candle::startTimeMs));
        return candles;
    }

    static long parseEpochMs(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0L;
        }
        if (node.isNumber()) {
            long raw = node.asLong();
            return raw > 100_000_000_000L ? raw : raw * 1000L;
        }
        return java.time.OffsetDateTime.parse(node.asText()).toInstant().toEpochMilli();
    }
}
