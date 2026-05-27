package com.tradej.core.domain.event;

import java.util.Map;

public record SignalSuppressed(
        EventMetadata metadata,
        String signalId,
        String symbol,
        String reason,
        Map<String, Object> gateContext
) implements DomainEvent {
}
