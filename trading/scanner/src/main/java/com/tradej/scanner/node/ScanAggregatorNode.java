package com.tradej.scanner.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.ScanHitProduced;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.pipeline.runtime.BasePipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates {@link ScanHitProduced} events from multiple criterion nodes,
 * deduplicates by symbol, ranks by score, and emits {@link ScanResultsPublished}
 * on configurable time windows or max hit thresholds.
 */
public final class ScanAggregatorNode extends BasePipelineNode {

    private static final Logger log = LoggerFactory.getLogger(ScanAggregatorNode.class);

    private final String profileId;
    private final long windowMs;
    private final int maxHits;

    private final ConcurrentHashMap<String, ScanHitProduced> accumulated = new ConcurrentHashMap<>();
    private volatile long windowStartMs = 0L;

    public ScanAggregatorNode(String profileId, long windowMs, int maxHits) {
        this.profileId = profileId;
        this.windowMs = Math.max(1000L, windowMs);
        this.maxHits = maxHits > 0 ? maxHits : 100;
    }

    @Override
    protected void onInit() {
        this.windowStartMs = context.getClockTimeMs();
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (!(event instanceof ScanHitProduced hit)) {
            return;
        }
        if (!hit.profileId().equals(profileId)) {
            return;
        }

        // Deduplicate by symbol: keep the highest score
        accumulated.compute(hit.symbol(), (key, existing) -> {
            if (existing == null || hit.score() > existing.score()) {
                return hit;
            }
            return existing;
        });

        // Flush on window expiry or max hits reached
        long now = context.getClockTimeMs();
        if (now - windowStartMs >= windowMs || accumulated.size() >= maxHits) {
            flush(now);
        }
    }

    private void flush(long now) {
        if (accumulated.isEmpty()) {
            windowStartMs = now;
            return;
        }

        // Sort by score descending, take top N
        List<ScanHitProduced> sorted = new ArrayList<>(accumulated.values());
        sorted.sort((a, b) -> Double.compare(b.score(), a.score()));
        int topN = Math.min(maxHits, sorted.size());
        sorted = sorted.subList(0, topN);

        // Build summary hits
        List<ScanResultsPublished.ScanHitSummary> hits = new ArrayList<>(topN);
        String runId = UUID.randomUUID().toString();
        long startedAt = windowStartMs;
        sorted.forEach(h -> hits.add(new ScanResultsPublished.ScanHitSummary(
                h.symbol(), h.exchangeSegment(), h.underlying(), h.score(), h.reasons()
        )));

        log.info("Scan aggregator flush profile={} window={}ms hits={} ranked={}",
                profileId, now - windowStartMs, accumulated.size(), topN);

        context.publish(new ScanResultsPublished(
                EventMetadata.root(),
                profileId, runId, hits.size(),
                startedAt, now, hits
        ));

        accumulated.clear();
        windowStartMs = context.getClockTimeMs();
    }
}
