package com.tradej.core.domain.event;

import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.Optional;

/**
 * Canonical market tick event -- the single event type for all streaming market data.
 */
public record MarketTickEvent(
    EventMetadata metadata,
    long sequenceId,
    String symbol,
    ExchangeSegment segment,
    FeedMode feedMode,
    long ltpPaisa,
    long lastTradeQuantity,
    long cumulativeVolume,
    long exchangeTimestampEpochMs,
    Optional<MarketDepth> depth,
    long openInterest,
    long oiForTheDay
) implements DomainEvent { 
    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
