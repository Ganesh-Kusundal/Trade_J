package com.tradej.core.domain.event;

import com.tradej.core.domain.model.MarketDepth;

/**
 * Deprecated in favor of {@link MarketTickEvent}.
 * <p>
 * {@link MarketTickEvent} is the canonical market tick event used by all
 * broker adapters, the hot path, and downstream consumers. This type is
 * retained for backward compatibility during replay of persisted events
 * and will be removed after all persisted data is migrated.
 */
@Deprecated(since = "2.0", forRemoval = true)
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
    public long sequenceId() {
        return metadata.sequenceId();
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
