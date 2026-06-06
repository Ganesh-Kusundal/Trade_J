package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.core.domain.model.DepthLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class IcebergDetector {

    private static final int MIN_REFRESH_COUNT = 2;
    private static final double REFRESH_RATIO_THRESHOLD = 0.5;
    private static final double CONFIDENCE_BASE = 0.6;
    private static final double CONFIDENCE_PER_REFRESH = 0.1;

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, LevelHistory>> histories = new ConcurrentHashMap<>();

    public List<DepthAnalyticsEvents.IcebergSignal> detect(OrderBook book) {
        var symbolHistory = histories.computeIfAbsent(book.symbol(), k -> new ConcurrentHashMap<>());
        List<DepthAnalyticsEvents.IcebergSignal> signals = new ArrayList<>();

        recordLevels(book.bids(), "BID", symbolHistory);
        recordLevels(book.asks(), "ASK", symbolHistory);

        for (var entry : symbolHistory.entrySet()) {
            LevelHistory history = entry.getValue();
            if (history.refreshCount >= MIN_REFRESH_COUNT) {
                double confidence = Math.min(0.99, CONFIDENCE_BASE + history.refreshCount * CONFIDENCE_PER_REFRESH);
                signals.add(new DepthAnalyticsEvents.IcebergSignal(
                        book.symbol(), book.segment().name(),
                        entry.getKey(), history.side,
                        confidence, history.estimatedTotalQty,
                        history.lastVisibleQty, history.refreshCount,
                        book.lastUpdateMs()));
            }
        }
        return signals;
    }

    private void recordLevels(List<DepthLevel> levels, String side,
                               ConcurrentHashMap<Long, LevelHistory> symbolHistory) {
        for (DepthLevel level : levels) {
            symbolHistory.compute(level.pricePaisa(), (price, existing) -> {
                if (existing == null) {
                    return new LevelHistory(side, level.quantity(), level.quantity(), 0, level.quantity());
                }
                long prevQty = existing.lastVisibleQty;
                long newQty = level.quantity();
                int refreshCount = existing.refreshCount;
                long estimatedTotal = existing.estimatedTotalQty;

                if (prevQty < newQty * REFRESH_RATIO_THRESHOLD && newQty > 0) {
                    refreshCount++;
                    estimatedTotal += newQty;
                }
                return new LevelHistory(side, newQty, Math.max(existing.peakQty, newQty),
                        refreshCount, estimatedTotal);
            });
        }
    }

    private record LevelHistory(String side, long lastVisibleQty, long peakQty,
                                 int refreshCount, long estimatedTotalQty) {}
}
