package com.tradej.broker.core.historical;

import com.tradej.core.domain.model.Candle;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HistoricalCandleMerger {

    private HistoricalCandleMerger() {
    }

    public static List<Candle> dedupeAndSort(List<Candle> candles) {
        Map<String, Candle> unique = new LinkedHashMap<>();
        for (Candle candle : candles) {
            unique.putIfAbsent(candle.startTimeMs() + "|" + candle.endTimeMs(), candle);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingLong(Candle::startTimeMs))
                .toList();
    }
}
