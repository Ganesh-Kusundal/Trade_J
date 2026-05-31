package com.tradej.core.domain.event;

import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.Optional;

/**
 * Canonical market tick event — the single event type for all streaming market data.
 * <p>
 * Every broker adapter normalizes its native tick format into this event.
 * The core engine (Disruptor pipeline, candle aggregation, strategy) consumes ONLY this.
 * <p>
 * {@link TickReceived} is deprecated and will be removed after Dhan adapter migration.
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
        Optional<MarketDepth> depth
) implements DomainEvent {
}
