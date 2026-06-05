package com.tradej.hotpath;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Validates data integrity across the market data pipeline.
 * Tracks event counts at each stage boundary to verify no data loss.
 *
 * <p>Usage:
 * <pre>{@code
 * PipelineDataIntegrityValidator validator = new PipelineDataIntegrityValidator();
 *
 * // At each stage boundary:
 * validator.recordIngress("SBIN");
 * validator.recordProcessed("SBIN");
 * validator.recordEgress("SBIN");
 *
 * // Periodically check:
 * PipelineDataIntegrityValidator.IntegrityReport report = validator.report();
 * assert report.allMatch() : "Data loss detected: " + report;
 * }</pre>
 */
public final class PipelineDataIntegrityValidator {

    private static final Logger log = LoggerFactory.getLogger(PipelineDataIntegrityValidator.class);

    private final ConcurrentHashMap<String, AtomicLong> ingressCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> processedCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> egressCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> droppedCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalIngress = new AtomicLong();
    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalEgress = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();

    /**
     * Record an event entering the pipeline (e.g., from WebSocket).
     */
    public void recordIngress(String symbol) {
        ingressCounts.computeIfAbsent(symbol, k -> new AtomicLong()).incrementAndGet();
        totalIngress.incrementAndGet();
    }

    /**
     * Record an event being processed by the pipeline (e.g., after rate limiting).
     */
    public void recordProcessed(String symbol) {
        processedCounts.computeIfAbsent(symbol, k -> new AtomicLong()).incrementAndGet();
        totalProcessed.incrementAndGet();
    }

    /**
     * Record an event exiting the pipeline (e.g., after Disruptor dispatch).
     */
    public void recordEgress(String symbol) {
        egressCounts.computeIfAbsent(symbol, k -> new AtomicLong()).incrementAndGet();
        totalEgress.incrementAndGet();
    }

    /**
     * Record a dropped event (e.g., rate limited, deduplicated).
     */
    public void recordDropped(String symbol) {
        droppedCounts.computeIfAbsent(symbol, k -> new AtomicLong()).incrementAndGet();
        totalDropped.incrementAndGet();
    }

    /**
     * Generate an integrity report.
     */
    public IntegrityReport report() {
        long ingress = totalIngress.get();
        long processed = totalProcessed.get();
        long egress = totalEgress.get();
        long dropped = totalDropped.get();

        boolean allMatch = (processed + dropped == ingress) && (egress <= processed);

        return new IntegrityReport(ingress, processed, egress, dropped, allMatch);
    }

    /**
     * Reset all counters.
     */
    public void reset() {
        ingressCounts.clear();
        processedCounts.clear();
        egressCounts.clear();
        droppedCounts.clear();
        totalIngress.set(0);
        totalProcessed.set(0);
        totalEgress.set(0);
        totalDropped.set(0);
    }

    /**
     * Get per-symbol ingress count.
     */
    public long ingressCount(String symbol) {
        AtomicLong count = ingressCounts.get(symbol);
        return count != null ? count.get() : 0;
    }

    /**
     * Get per-symbol processed count.
     */
    public long processedCount(String symbol) {
        AtomicLong count = processedCounts.get(symbol);
        return count != null ? count.get() : 0;
    }

    /**
     * Get per-symbol egress count.
     */
    public long egressCount(String symbol) {
        AtomicLong count = egressCounts.get(symbol);
        return count != null ? count.get() : 0;
    }

    /**
     * Integrity report with counts and match status.
     */
    public record IntegrityReport(
            long totalIngress,
            long totalProcessed,
            long totalEgress,
            long totalDropped,
            boolean allMatch
    ) {
        @Override
        public String toString() {
            return String.format(
                    "IntegrityReport{ingress=%d, processed=%d, egress=%d, dropped=%d, match=%s}",
                    totalIngress, totalProcessed, totalEgress, totalDropped, allMatch
            );
        }
    }
}
