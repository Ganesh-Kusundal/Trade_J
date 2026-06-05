package com.tradej.core.domain.event;

import java.util.Map;

/**
 * Periodic mark-to-market unrealized P&amp;L snapshot for risk monitoring and UI.
 */
public record UnrealizedPnLUpdated(
        EventMetadata metadata,
        long unrealizedPnlPaisa,
        long realizedLossPaisa,
        long totalLossPaisa,
        Map<String, Long> symbolUnrealizedPaisa
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
