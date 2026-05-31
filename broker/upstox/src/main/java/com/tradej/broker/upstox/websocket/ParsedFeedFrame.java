package com.tradej.broker.upstox.websocket;

import com.tradej.core.domain.model.DepthLevel;

import java.util.Arrays;
import java.util.List;

/**
 * Parsed binary feed frame from Upstox WebSocket.
 */
public record ParsedFeedFrame(
        long instrumentToken,
        long ltpPaisa,
        long lastTradeQuantity,
        long openInterest,
        long volume,
        long exchangeTimestampMs,
        DepthLevel[] bids,
        DepthLevel[] asks,
        int frameType
) {
    /**
     * Returns bids as a list, or empty list if no depth data.
     */
    public List<DepthLevel> bidList() {
        return bids != null ? List.copyOf(Arrays.asList(bids)) : List.of();
    }

    /**
     * Returns asks as a list, or empty list if no depth data.
     */
    public List<DepthLevel> askList() {
        return asks != null ? List.copyOf(Arrays.asList(asks)) : List.of();
    }

    /**
     * Returns true if this frame contains depth data.
     */
    public boolean hasDepth() {
        return bids != null || asks != null;
    }

    /**
     * Returns true if this frame contains LTP data.
     */
    public boolean hasLtp() {
        return ltpPaisa > 0;
    }

    /**
     * A single depth level.
     */
    public record DepthLevel(long pricePaisa, long quantity, int orderCount) {}
}
