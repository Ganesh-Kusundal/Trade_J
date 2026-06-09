package com.tradej.core.domain.event;

/**
 * Visitor for market data events: ticks, depth updates, candles, and derived analytics.
 */
public interface MarketDataVisitor {
    default void visit(MarketTickEvent event) {}
    default void visit(DepthUpdateEvent event) {}
    default void visit(CandleClosed event) {}
    default void visit(CandleDeveloping event) {}
    default void visit(OptionChainUpdated event) {}
    default void visit(GreeksComputed event) {}
    default void visit(GammaExposureComputed event) {}
    default void visit(MaxPainComputed event) {}
}
