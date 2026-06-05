package com.tradej.core.domain.event;

/**
 * Published when the simulated or live P&L changes.
 */
public record PnlUpdatedEvent(
        EventMetadata metadata,
        long realizedPnlPaisa,
        long unrealizedPnlPaisa,
        long netExposurePaisa
) implements DomainEvent {
    public PnlUpdatedEvent {
        metadata = metadata == null ? EventMetadata.root() : metadata;
    }

    @Override
    public EventMetadata metadata() {
        return metadata;
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
