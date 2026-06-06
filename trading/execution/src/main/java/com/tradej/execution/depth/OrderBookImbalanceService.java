package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;

public final class OrderBookImbalanceService {

    public DepthAnalyticsEvents.DepthImbalanceSnapshot compute(OrderBook book) {
        double topOfBook = book.topOfBookImbalance();
        double cumulative = book.cumulativeImbalance();
        String trend = classifyTrend(topOfBook, cumulative);
        return new DepthAnalyticsEvents.DepthImbalanceSnapshot(
                book.symbol(), book.segment().name(),
                topOfBook, cumulative, trend, book.lastUpdateMs());
    }

    private static String classifyTrend(double topOfBook, double cumulative) {
        if (topOfBook > 0.3 && cumulative > 0.2) return "STRONG_BID";
        if (topOfBook < -0.3 && cumulative < -0.2) return "STRONG_ASK";
        if (Math.abs(topOfBook) < 0.1 && Math.abs(cumulative) < 0.1) return "NEUTRAL";
        return "MIXED";
    }
}
