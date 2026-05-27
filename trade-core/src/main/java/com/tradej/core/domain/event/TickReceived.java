package com.tradej.core.domain.event;

import com.tradej.core.domain.model.MarketDepth;

public record TickReceived(
        EventMetadata metadata,
        String symbol,
        String interval,
        long ltpPaisa,
        long lastTradeQuantity,
        long cumulativeVolume,
        long exchangeTimestampMs,
        MarketDepth marketDepth
) implements DomainEvent {
}
