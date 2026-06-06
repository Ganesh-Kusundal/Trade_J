package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.core.domain.model.DepthLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class AbsorptionAnalyzer {

    private static final double EROSION_THRESHOLD = 0.3;
    private static final int MIN_OBSERVATIONS = 3;

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AbsorptionState>> states = new ConcurrentHashMap<>();

    public List<DepthAnalyticsEvents.AbsorptionSignal> analyze(OrderBook book) {
        var symbolStates = states.computeIfAbsent(book.symbol(), k -> new ConcurrentHashMap<>());
        List<DepthAnalyticsEvents.AbsorptionSignal> signals = new ArrayList<>();

        trackSide(book.bids(), "BID", symbolStates);
        trackSide(book.asks(), "ASK", symbolStates);

        for (var entry : symbolStates.entrySet()) {
            AbsorptionState state = entry.getValue();
            if (state.observations >= MIN_OBSERVATIONS && state.erosionRate > EROSION_THRESHOLD) {
                double breakoutProb = Math.min(0.95, state.erosionRate * 0.8);
                signals.add(new DepthAnalyticsEvents.AbsorptionSignal(
                        book.symbol(), book.segment().name(),
                        entry.getKey(), state.side,
                        state.erosionRate, breakoutProb,
                        state.isCancellation, book.lastUpdateMs()));
            }
        }
        return signals;
    }

    private void trackSide(List<DepthLevel> levels, String side,
                            ConcurrentHashMap<Long, AbsorptionState> symbolStates) {
        for (DepthLevel level : levels) {
            symbolStates.compute(level.pricePaisa(), (price, existing) -> {
                if (existing == null) {
                    return new AbsorptionState(side, level.quantity(), level.quantity(), 1, 0.0, false);
                }
                long peakQty = Math.max(existing.peakQty, level.quantity());
                double erosionRate = peakQty > 0 ? 1.0 - ((double) level.quantity() / peakQty) : 0.0;
                boolean isCancellation = level.quantity() == 0 && existing.lastQty > 0;
                return new AbsorptionState(side, level.quantity(), peakQty,
                        existing.observations + 1, erosionRate, isCancellation);
            });
        }
    }

    private record AbsorptionState(String side, long lastQty, long peakQty,
                                    int observations, double erosionRate, boolean isCancellation) {}
}
