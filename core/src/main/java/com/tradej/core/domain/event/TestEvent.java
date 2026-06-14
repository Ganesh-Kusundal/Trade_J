package com.tradej.core.domain.event;

/**
 * Sentinel event used as a generic stub in unit tests.
 */
public record TestEvent(EventMetadata metadata) implements DomainEvent {

    public TestEvent() {
        this(EventMetadata.root());
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        // Sentinel event, no-op for visitors
    }
}
