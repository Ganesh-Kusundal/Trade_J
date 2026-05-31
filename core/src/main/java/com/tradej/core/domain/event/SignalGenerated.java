package com.tradej.core.domain.event;

import com.tradej.core.domain.value.Side;

import java.util.Map;

public record SignalGenerated(
        EventMetadata metadata,
        String signalId,
        String symbol,
        String interval,
        Side side,
        long entryPricePaisa,
        long stopLossPaisa,
        long takeProfitPaisa,
        String setup,
        Map<String, Object> attributes
) implements DomainEvent {
    public SignalGenerated {
        attributes = Map.copyOf(attributes);
    }
}
