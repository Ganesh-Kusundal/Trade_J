package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.core.domain.model.DepthLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class RestingOrderAnalyzer {

    private static final int VOLUME_SPIKE_MULTIPLIER = 3;
    private static final int MIN_PERSISTENCE_UPDATES = 3;
    private static final int MAX_LEVELS = 10;

    private final ConcurrentHashMap<String, LevelTracker> trackers = new ConcurrentHashMap<>();

    public DepthAnalyticsEvents.SRLevelsUpdate analyze(OrderBook book) {
        LevelTracker tracker = trackers.computeIfAbsent(book.symbol(), k -> new LevelTracker());
        tracker.record(book);
        List<DepthAnalyticsEvents.SRLevelsUpdate.SRLevel> levels = tracker.computeLevels();
        return new DepthAnalyticsEvents.SRLevelsUpdate(
                book.symbol(), book.segment().name(), levels, book.lastUpdateMs());
    }

    private static final class LevelTracker {
        private final ConcurrentHashMap<Long, int[]> levelStats = new ConcurrentHashMap<>();

        void record(OrderBook book) {
            for (DepthLevel bid : book.bids()) {
                levelStats.compute(bid.pricePaisa(), (k, v) -> {
                    if (v == null) return new int[]{(int) bid.quantity(), 1};
                    v[0] += (int) bid.quantity();
                    v[1]++;
                    return v;
                });
            }
            for (DepthLevel ask : book.asks()) {
                levelStats.compute(ask.pricePaisa(), (k, v) -> {
                    if (v == null) return new int[]{(int) ask.quantity(), 1};
                    v[0] += (int) ask.quantity();
                    v[1]++;
                    return v;
                });
            }
        }

        List<DepthAnalyticsEvents.SRLevelsUpdate.SRLevel> computeLevels() {
            if (levelStats.isEmpty()) return List.of();

            double avgVol = levelStats.values().stream()
                    .mapToDouble(v -> (double) v[0] / Math.max(v[1], 1))
                    .average().orElse(1.0);

            List<DepthAnalyticsEvents.SRLevelsUpdate.SRLevel> levels = new ArrayList<>();
            for (var entry : levelStats.entrySet()) {
                long price = entry.getKey();
                int[] stats = entry.getValue();
                int avgLevelVol = stats[0] / Math.max(stats[1], 1);
                if (avgLevelVol > avgVol * VOLUME_SPIKE_MULTIPLIER && stats[1] >= MIN_PERSISTENCE_UPDATES) {
                    double persistence = Math.min(1.0, (double) stats[1] / 10.0);
                    levels.add(new DepthAnalyticsEvents.SRLevelsUpdate.SRLevel(
                            price, price + 5000L, "BID", stats[0], persistence));
                }
            }
            levels.sort(Comparator.comparingDouble(DepthAnalyticsEvents.SRLevelsUpdate.SRLevel::persistenceScore).reversed());
            return levels.stream().limit(MAX_LEVELS).toList();
        }
    }
}
