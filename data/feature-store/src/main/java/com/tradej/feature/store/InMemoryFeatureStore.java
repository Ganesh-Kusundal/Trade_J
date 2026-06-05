package com.tradej.feature.store;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.FeatureGenerator;
import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.port.FeatureStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory feature store updated synchronously on the hot path so strategy
 * plugins can read features for the just-closed candle before async DuckDB
 * persistence completes.
 */
public final class InMemoryFeatureStore implements FeatureStore {

    private static final int MAX_CANDLES_PER_KEY = 512;

    private final Map<CandleKey, List<Candle>> candlesByKey = new ConcurrentHashMap<>();

    @Override
    public void feed(DomainEvent event) {
        switch (event) {
            case CandleDeveloping developing -> upsertCandle(developing.candle(), false);
            case CandleClosed closed -> upsertCandle(closed.candle(), true);
            case TickReceived ignored -> { }
            case MarketTickEvent ignored -> { }
            default -> { }
        }
    }

    @Override
    public Optional<FeatureVector> getFeatures(String symbol, String interval, int lookback) {
        List<Candle> candles = candlesByKey.get(new CandleKey(symbol, interval));
        if (candles == null || candles.isEmpty()) {
            return Optional.empty();
        }
        int fetchCount = Math.max(lookback + 1, 5);
        if (candles.size() < Math.min(lookback, 5)) {
            return Optional.empty();
        }
        int actualFetch = Math.min(fetchCount, candles.size());
        List<Candle> slice = candles.subList(candles.size() - actualFetch, candles.size());
        return FeatureGenerator.compute(slice, lookback);
    }

    private void upsertCandle(Candle candle, boolean closed) {
        CandleKey key = new CandleKey(candle.symbol(), candle.interval());
        candlesByKey.compute(key, (ignored, existing) -> {
            List<Candle> list = existing;
            int index = findInsertionIndex(list, candle.startTimeMs());
            Candle stored = new Candle(
                    candle.symbol(), candle.interval(),
                    candle.startTimeMs(), candle.endTimeMs(),
                    candle.openPaisa(), candle.highPaisa(), candle.lowPaisa(),
                    candle.closePaisa(), candle.volume(), closed
            );
            if (list != null && index >= 0 && index < list.size()
                    && list.get(index).startTimeMs() == candle.startTimeMs()) {
                // Existing candle — replace in-place
                list.set(index, stored);
                return list;
            }
            // New candle — create or copy to grow
            List<Candle> newList = list == null
                    ? new ArrayList<>()
                    : new ArrayList<>(list);
            if (index < 0) {
                newList.add(stored);
            } else {
                newList.add(index, stored);
            }
            if (newList.size() > MAX_CANDLES_PER_KEY) {
                return new ArrayList<>(newList.subList(
                        newList.size() - MAX_CANDLES_PER_KEY, newList.size()));
            }
            return newList;
        });
    }

    /**
     * Binary search for the insertion point of a given timestamp.
     * Returns the index where a candle with this timestamp should be,
     * or a negative value if not found. The caller checks for exact match.
     */
    private static int findInsertionIndex(List<Candle> candles, long startTimeMs) {
        if (candles == null || candles.isEmpty()) {
            return -1;
        }
        int lo = 0;
        int hi = candles.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long midVal = candles.get(mid).startTimeMs();
            if (midVal < startTimeMs) {
                lo = mid + 1;
            } else if (midVal > startTimeMs) {
                hi = mid - 1;
            } else {
                return mid; // exact match
            }
        }
        return lo; // insertion point
    }

    /**
     * Captures candle state for replay isolation (PR-11).
     */
    public StateSnapshot snapshot() {
        Map<CandleKey, List<Candle>> copy = new HashMap<>();
        candlesByKey.forEach((key, candles) -> copy.put(key, List.copyOf(candles)));
        return new StateSnapshot(copy);
    }

    /**
     * Restores candle state from a prior {@link #snapshot()}. A {@code null} snapshot is a no-op.
     */
    public void restore(StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        candlesByKey.clear();
        snapshot.candles().forEach((key, candles) ->
                candlesByKey.put(key, new ArrayList<>(candles)));
    }

    public record StateSnapshot(Map<CandleKey, List<Candle>> candles) {
        public StateSnapshot {
            candles = Map.copyOf(candles);
        }
    }

    private record CandleKey(String symbol, String interval) {
    }
}
