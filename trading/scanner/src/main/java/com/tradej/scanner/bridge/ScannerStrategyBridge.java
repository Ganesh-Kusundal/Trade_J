package com.tradej.scanner.bridge;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import com.tradej.core.domain.port.DomainEventHandler;

/**
 * Bridges the Scanner engine to the Strategy/Execution pipeline.
 *
 * <p>Listens for {@link ScanResultsPublished} events and transforms
 * qualifying scan hits into {@link SignalGenerated} events that flow
 * downstream to strategy confirmation, risk checks, and order execution.
 *
 * <p>This is the missing link between the scanner (which identifies
 * trade-worthy instruments) and the execution pipeline (which places
 * orders). Without this bridge, scanner results exist in isolation
 * with no path to becoming actual trades.
 *
 * <p>Usage:
 * <pre>{@code
 *   ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);
 *   bridge.start();
 *   // Scanner events now flow into strategy/execution pipeline
 * }</pre>
 *
 * <p><b>Design notes:</b>
 * <ul>
 *   <li>Minimum score threshold prevents low-quality hits from becoming signals</li>
 *   <li>Exchange segment resolved from hit metadata, defaulting to NSE_EQ</li>
 *   <li>Entry/stop/target prices require market data context (LTP injection)</li>
 *   <li>Signal attributes carry scanner context (profileId, criterion, score, reasons)</li>
 * </ul>
 */
public final class ScannerStrategyBridge {

    private static final Logger log = LoggerFactory.getLogger(ScannerStrategyBridge.class);

    /** Scanner hits below this score threshold are suppressed. */
    private static final double DEFAULT_MIN_SCORE = 0.1;

    /** Default position quantity in shares for scanner-generated signals. */
    private static final long DEFAULT_QUANTITY = 25L;

    private final EventBus eventBus;
    private final double minScore;
    private final long defaultQuantity;
    private final DomainEventHandler<ScanResultsPublished> handler;

    private volatile boolean running;

    public ScannerStrategyBridge(EventBus eventBus) {
        this(eventBus, DEFAULT_MIN_SCORE, DEFAULT_QUANTITY);
    }

    public ScannerStrategyBridge(EventBus eventBus, double minScore, long defaultQuantity) {
        this.eventBus = eventBus;
        this.minScore = minScore;
        this.defaultQuantity = defaultQuantity;
        this.handler = this::onScanResults;
    }

    /**
     * Start the bridge — subscribes to scan results on the event bus.
     * Idempotent: calling start() multiple times has no effect.
     */
    public void start() {
        if (running) return;
        running = true;
        eventBus.subscribe(ScanResultsPublished.class, handler);
        log.info("ScannerStrategyBridge started — minScore={} defaultQuantity={}", minScore, defaultQuantity);
    }

    /**
     * Stop the bridge — unsubscribes from scan results.
     */
    public void stop() {
        if (!running) return;
        running = false;
        eventBus.unsubscribe(ScanResultsPublished.class, handler);
        log.info("ScannerStrategyBridge stopped");
    }

    public boolean isRunning() {
        return running;
    }

    // ── Event handler ──────────────────────────────────────────────

    private void onScanResults(ScanResultsPublished results) {
        if (!running) return;

        log.debug("Scanner results received: profileId={} hitCount={}", results.profileId(), results.hitCount());

        for (ScanResultsPublished.ScanHitSummary hit : results.hits()) {
            if (hit.score() < minScore) {
                log.trace("Scanner hit suppressed (score below threshold): symbol={} score={}", hit.symbol(), hit.score());
                continue;
            }

            SignalGenerated signal = transform(results, hit);
            eventBus.publish(signal);

            log.info("Scanner hit → Signal: symbol={} score={} profileId={} signalId={}",
                    hit.symbol(), hit.score(), results.profileId(), signal.signalId());
        }
    }

    /**
     * Transforms a single scanner hit into a {@link SignalGenerated} event.
     *
     * <p>The entry price and stop/target are set to 0 here because market data
     * context (LTP) must be resolved at execution time. The ExecutionHandler
     * or risk layer can inject LTP-based prices before sending to broker.
     * The scanner's role is to identify what to trade; the market data feed
     * determines at what price.
     */
    private SignalGenerated transform(ScanResultsPublished results, ScanResultsPublished.ScanHitSummary hit) {
        ExchangeSegment segment = resolveExchangeSegment(hit.exchangeSegment());

        Map<String, Object> attrs = Map.of(
                "quantity", defaultQuantity,
                "exchangeSegment", segment.name(),
                "profileId", results.profileId(),
                "criterionTypes", hit.reasons() != null && !hit.reasons().isEmpty() ? String.join(",", hit.reasons()) : "scanner",
                "scannerScore", hit.score(),
                "scannerRunId", results.runId(),
                "setup", "scanner-signal"
        );

        return new SignalGenerated(
                EventMetadata.root(),
                UUID.randomUUID().toString(),
                hit.symbol(),
                "scanner",           // interval — scanner is not timeframe-bound
                Side.BUY,            // scanner hits are candidates; side determined by strategy
                0L,                  // entryPricePaisa — injected by market data at execution
                0L,                  // stopLossPaisa — calculated by risk layer
                0L,                  // takeProfitPaisa — calculated by risk layer
                "scanner-hit",
                attrs
        );
    }

    private static ExchangeSegment resolveExchangeSegment(String segmentName) {
        if (segmentName == null || segmentName.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        try {
            return ExchangeSegment.valueOf(segmentName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown exchange segment '{}' — defaulting to NSE_EQ", segmentName);
            return ExchangeSegment.NSE_EQ;
        }
    }
}
