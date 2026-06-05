package com.tradej.core.domain.event;

public record KillSwitchEngaged(
        EventMetadata metadata,
        String reason,
        long balancePaisa,
        long equityPaisa,
        long dailyLossPaisa,
        int consecutiveLosses
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
