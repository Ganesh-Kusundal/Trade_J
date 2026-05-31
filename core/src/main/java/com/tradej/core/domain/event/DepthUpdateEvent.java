package com.tradej.core.domain.event;

import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;

/**
 * Canonical depth update event — emitted by broker adapters for standalone depth-of-book streaming.
 * Brokers that provide depth-only feeds (separate from tick/quote feeds) use this.
 */
public record DepthUpdateEvent(
        EventMetadata metadata,
        String symbol,
        ExchangeSegment segment,
        List<DepthLevel> bids,
        List<DepthLevel> asks,
        int levels,
        long exchangeTimestampMs
) implements DomainEvent {
    public DepthUpdateEvent {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
