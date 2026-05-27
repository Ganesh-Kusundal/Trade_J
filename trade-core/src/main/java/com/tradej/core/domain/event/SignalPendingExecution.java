package com.tradej.core.domain.event;

import com.tradej.core.domain.model.OrderRequest;

import java.util.Map;

public record SignalPendingExecution(
        EventMetadata metadata,
        String signalId,
        OrderRequest orderRequest,
        Map<String, Object> decisionContext
) implements DomainEvent {
}
