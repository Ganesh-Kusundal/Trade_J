package com.tradej.core.domain.event;

/**
 * Sentinel event used to signal thread/handler shutdown in a type-safe manner.
 */
public record PoisonPillEvent(EventMetadata metadata) implements DomainEvent {

    public PoisonPillEvent() {
        this(EventMetadata.root());
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        // Sentinel event, no-op for visitors
    }
}
