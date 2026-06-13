package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically triggers broker position reconciliation via {@link OrderReconciler}.
 *
 * <p>Runs two reconciliation passes on each tick:
 * <ol>
 *   <li><b>Expected-vs-broker</b> — compares expected net positions (from strategy
 *       engine) against live broker positions via {@link OrderReconciler#reconcile}.</li>
 *   <li><b>OSM-vs-broker</b> — compares OSM-tracked filled orders against live broker
 *       positions via {@link OrderReconciler#reconcileAll}.</li>
 * </ol>
 *
 * <p>Runs on a fixed rate configured via {@code trade.reconciliation.interval-seconds}
 * in {@code application.yml}, with an initial delay configured via
 * {@code trade.reconciliation.initial-delay-seconds}.
 *
 * <p>Reconciliation mismatches (if any) are published to the {@link EventBus}
 * so they can be logged, alerted on, or trigger corrective actions.
 *
 * <p><b>P3.5 full wiring (2026-06-12):</b> the scheduler now also feeds
 * {@link PositionMismatch} events into the injected
 * {@link MismatchHandler} (default: log only). Pass a handler that calls
 * {@link PositionService#applyBrokerSnapshot} to correct the canonical
 * state on detected mismatches. The full live-enablement gate (drift →
 * bracket order / exit) is a follow-up.
 *
 * <p>If no {@link NetPositionProvider} bean is available in the context, the expected-vs-broker
 * pass is skipped with an empty map (no mismatches flagged).
 */
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final OrderReconciler orderReconciler;
    private final EventBus eventBus;
    private final NetPositionProvider netPositionProvider;
    private final MismatchHandler mismatchHandler;

    public ReconciliationScheduler(
            OrderReconciler orderReconciler,
            EventBus eventBus,
            NetPositionProvider netPositionProvider
    ) {
        this(orderReconciler, eventBus, netPositionProvider, MismatchHandler.logging());
    }

    public ReconciliationScheduler(
            OrderReconciler orderReconciler,
            EventBus eventBus,
            NetPositionProvider netPositionProvider,
            MismatchHandler mismatchHandler
    ) {
        this.orderReconciler = orderReconciler;
        this.eventBus = eventBus;
        this.netPositionProvider = netPositionProvider;
        this.mismatchHandler = mismatchHandler;
    }

    /**
     * Strategy for handling a detected {@link PositionMismatch}. The default
     * implementation logs and does nothing else (P3.5 dry-run). Pass a custom
     * handler to apply the broker's view to {@link PositionService} (P3.5 wired).
     */
    @FunctionalInterface
    public interface MismatchHandler {
        void onMismatch(PositionMismatch mismatch);

        static MismatchHandler logging() {
            return mismatch -> log.warn(
                    "Reconciliation mismatch symbol={} paper={} broker={} engineKey={} (no auto-correction)",
                    mismatch.symbol(), mismatch.paperQuantity(),
                    mismatch.brokerQuantity(), mismatch.engineKey());
        }
    }

    /**
     * Runs both expected-vs-broker and OSM reconciliation on a fixed schedule.
     *
     * <p>Publishes all {@link com.tradej.core.domain.event.PositionMismatch PositionMismatch}
     * events through the event bus for downstream alerting/logging, and feeds
     * each mismatch into the injected {@link MismatchHandler}.
     */
    public void reconcilePeriodically() {
        log.debug("Starting periodic reconciliation");

        // Pass 1: expected net positions (from strategy engine) vs broker positions
        try {
            orderReconciler.reconcile(netPositionProvider.getNetPositions(), this::publishEvent);
        } catch (Exception e) {
            log.error("Expected-vs-broker reconciliation failed", e);
        }

        // Pass 2: OSM-tracked filled orders vs broker positions
        try {
            orderReconciler.reconcileAll(this::publishEvent);
        } catch (Exception e) {
            log.error("OSM-vs-broker reconciliation failed", e);
        }

        log.debug("Completed periodic reconciliation");
    }

    private void publishEvent(com.tradej.core.domain.event.DomainEvent event) {
        eventBus.publish(event);
        if (event instanceof PositionMismatch mm) {
            try {
                mismatchHandler.onMismatch(mm);
            } catch (Exception e) {
                log.warn("MismatchHandler failed for symbol={}: {}",
                        mm.symbol(), e.getMessage());
            }
        }
    }
}
