package com.tradej.core.domain.event;

import java.util.Map;

public record SignalSuppressed(
        EventMetadata metadata,
        String signalId,
        String symbol,
        String reason,
        Map<String, Object> gateContext
) implements DomainEvent {
    public SignalSuppressed {
        gateContext = Map.copyOf(gateContext);
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
