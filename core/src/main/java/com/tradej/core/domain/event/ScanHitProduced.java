package com.tradej.core.domain.event;

import java.util.List;
import java.util.Map;

/**
 * Emitted when a streaming scanner criterion produces a hit.
 * Consumed by {@code ScanAggregatorNode} for deduplication, scoring, and ranking.
 * <p>
 * Uses only {@code String} fields for value types to avoid cross-module dependencies.
 */
public record ScanHitProduced(
        EventMetadata metadata,
        String profileId,
        String criterionType,
        String symbol,
        String exchangeSegment,
        String assetClass,
        String underlying,
        double score,
        List<String> reasons,
        Map<String, Object> snapshotFields
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
