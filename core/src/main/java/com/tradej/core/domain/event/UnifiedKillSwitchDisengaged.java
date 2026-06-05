package com.tradej.core.domain.event;

/**
 * Platform and broker kill switches cleared.
 */
public record UnifiedKillSwitchDisengaged(
        EventMetadata metadata
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
