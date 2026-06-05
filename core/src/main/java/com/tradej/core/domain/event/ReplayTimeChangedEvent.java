package com.tradej.core.domain.event;

/**
 * Published when the replay clock advances to a new timestamp.
 * Used by the gateway to propagate replay progress to WebSocket clients.
 */
public record ReplayTimeChangedEvent(
        EventMetadata metadata,
        long currentTimeMs,
        long replaySpeedNanos
) implements DomainEvent {
    public ReplayTimeChangedEvent {
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
