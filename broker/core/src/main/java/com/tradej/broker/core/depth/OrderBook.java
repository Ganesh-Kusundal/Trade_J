package com.tradej.broker.core.depth;

import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a live order book for a single instrument.
 * Maintains bid and ask depth levels and provides snapshot extraction.
 */
public final class OrderBook {

    private final String symbol;
    private final ExchangeSegment segment;
    private volatile List<DepthLevel> bids;
    private volatile List<DepthLevel> asks;
    private volatile long lastUpdateMs;

    public OrderBook(String symbol, ExchangeSegment segment) {
        this.symbol = symbol;
        this.segment = segment;
        this.bids = List.of();
        this.asks = List.of();
        this.lastUpdateMs = System.currentTimeMillis();
    }

    public synchronized void update(List<DepthLevel> newBids, List<DepthLevel> newAsks) {
        this.bids = List.copyOf(newBids);
        this.asks = List.copyOf(newAsks);
        this.lastUpdateMs = System.currentTimeMillis();
    }

    public String symbol() { return symbol; }
    public ExchangeSegment segment() { return segment; }
    public List<DepthLevel> bids() { return bids; }
    public List<DepthLevel> asks() { return asks; }
    public long lastUpdateMs() { return lastUpdateMs; }

    public long totalBidQuantity() {
        return bids.stream().mapToLong(DepthLevel::quantity).sum();
    }

    public long totalAskQuantity() {
        return asks.stream().mapToLong(DepthLevel::quantity).sum();
    }

    public long bestBidPaisa() {
        return bids.isEmpty() ? 0 : bids.getFirst().pricePaisa();
    }

    public long bestAskPaisa() {
        return asks.isEmpty() ? 0 : asks.getFirst().pricePaisa();
    }

    public double imbalanceRatio() {
        long totalBid = totalBidQuantity();
        long totalAsk = totalAskQuantity();
        if (totalBid + totalAsk == 0) return 0.0;
        return (double) totalBid / (totalBid + totalAsk);
    }

    public long midPricePaisa() {
        long bid = bestBidPaisa();
        long ask = bestAskPaisa();
        return (bid > 0 && ask > 0) ? (bid + ask) / 2 : 0;
    }

    public long spreadPaisa() {
        long bid = bestBidPaisa();
        long ask = bestAskPaisa();
        return (bid > 0 && ask > 0) ? ask - bid : 0;
    }

    /** Top-of-book imbalance: (bidQty - askQty) / (bidQty + askQty). Range: -1 to +1. */
    public double topOfBookImbalance() {
        long bidQty = bids.isEmpty() ? 0 : bids.getFirst().quantity();
        long askQty = asks.isEmpty() ? 0 : asks.getFirst().quantity();
        long total = bidQty + askQty;
        return total == 0 ? 0.0 : (double)(bidQty - askQty) / total;
    }

    /** Cumulative imbalance across all levels. Range: -1 to +1. */
    public double cumulativeImbalance() {
        long bidVol = totalBidQuantity();
        long askVol = totalAskQuantity();
        long total = bidVol + askVol;
        return total == 0 ? 0.0 : (double)(bidVol - askVol) / total;
    }

    /** Alias for totalBidQuantity (used by analytics engines). */
    public long totalBidVolume() { return totalBidQuantity(); }
    /** Alias for totalAskQuantity (used by analytics engines). */
    public long totalAskVolume() { return totalAskQuantity(); }

    public OrderBookSnapshot toSnapshot(int levels) {
        List<DepthLevel> snapBids = bids.size() > levels ? bids.subList(0, levels) : bids;
        List<DepthLevel> snapAsks = asks.size() > levels ? asks.subList(0, levels) : asks;
        return new OrderBookSnapshot(
                symbol,
                segment.name(),
                List.copyOf(snapBids),
                List.copyOf(snapAsks),
                midPricePaisa(),
                spreadPaisa(),
                totalBidQuantity(),
                totalAskQuantity(),
                cumulativeImbalance(),
                lastUpdateMs
        );
    }

    public record OrderBookSnapshot(
            String symbol,
            String segment,
            List<DepthLevel> bids,
            List<DepthLevel> asks,
            long midPricePaisa,
            long spreadPaisa,
            long cumulativeBidVol,
            long cumulativeAskVol,
            double imbalance,
            long timestampMs
    ) {}
}
