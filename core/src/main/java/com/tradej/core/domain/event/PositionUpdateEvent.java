package com.tradej.core.domain.event;

import com.tradej.core.domain.value.ExchangeSegment;

/**
 * Canonical position update event — emitted by broker adapters for live position streaming.
 * Used by PortfolioEngine for real-time P&L tracking.
 */
public record PositionUpdateEvent(
        EventMetadata metadata,
        String symbol,
        ExchangeSegment segment,
        long netQuantity,
        long averagePricePaisa,
        long ltpPaisa,
        long unrealizedPnlPaisa,
        long realizedPnlPaisa
) implements DomainEvent {
}
