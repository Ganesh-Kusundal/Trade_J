package com.tradej.core.domain.event;

/**
 * Published when position reconciliation detects a mismatch that requires trading halt.
 */
public record ReconciliationHaltRequired(
        EventMetadata metadata,
        String symbol,
        long expectedQuantity,
        long brokerQuantity,
        String engineKey,
        long mismatchQuantity,
        String reason
) implements DomainEvent {
    @Override
    public EventPriority priority() {
        return EventPriority.URGENT;
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
