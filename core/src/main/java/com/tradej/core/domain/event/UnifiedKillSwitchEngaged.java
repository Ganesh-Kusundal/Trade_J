package com.tradej.core.domain.event;

/**
 * Platform and broker kill switches engaged together.
 */
public record UnifiedKillSwitchEngaged(
        EventMetadata metadata,
        String reason
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
