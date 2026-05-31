package com.tradej.core.domain.event;

import java.util.List;

/**
 * Emitted by ScanAggregatorNode when a window of accumulated scan hits is flushed.
 * Contains ranked, deduplicated hits ready for persistence or downstream processing.
 */
public record ScanResultsPublished(
        EventMetadata metadata,
        String profileId,
        String runId,
        int hitCount,
        long startedAtMs,
        long finishedAtMs,
        List<ScanHitSummary> hits
) implements DomainEvent {

    public record ScanHitSummary(
            String symbol,
            String exchangeSegment,
            String underlying,
            double score,
            List<String> reasons
    ) {
    }
}
