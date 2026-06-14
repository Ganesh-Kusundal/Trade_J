package com.tradej.core.domain.event;

import java.util.Map;

/**
 * Snapshot of all per-strategy metrics published periodically. The
 * payload is a flat map keyed by {@code "strategy|eventType|outcome"}
 * (matching {@code StrategyMetrics.snapshot()}).
 */
public record StrategyMetricsSnapshot(
        EventMetadata metadata,
        Map<String, Long> counters
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
