package com.tradej.execution.reconcile;

import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
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
 * <p>If no {@link NetPositionProvider} bean is available in the context, the expected-vs-broker
 * pass is skipped with an empty map (no mismatches flagged).
 */
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final OrderReconciler orderReconciler;
    private final EventBus eventBus;
    private final NetPositionProvider netPositionProvider;

    public ReconciliationScheduler(
            OrderReconciler orderReconciler,
            EventBus eventBus,
            NetPositionProvider netPositionProvider
    ) {
        this.orderReconciler = orderReconciler;
        this.eventBus = eventBus;
        this.netPositionProvider = netPositionProvider;
    }

    /**
     * Runs both expected-vs-broker and OSM reconciliation on a fixed schedule.
     *
     * <p>Publishes all {@link com.tradej.core.domain.event.PositionMismatch PositionMismatch}
     * events through the event bus for downstream alerting/logging.
     */
    public void reconcilePeriodically() {
        log.debug("Starting periodic reconciliation");

        // Pass 1: expected net positions (from strategy engine) vs broker positions
        try {
            orderReconciler.reconcile(netPositionProvider.getNetPositions(), eventBus::publish);
        } catch (Exception e) {
            log.error("Expected-vs-broker reconciliation failed", e);
        }

        // Pass 2: OSM-tracked filled orders vs broker positions
        try {
            orderReconciler.reconcileAll(eventBus::publish);
        } catch (Exception e) {
            log.error("OSM-vs-broker reconciliation failed", e);
        }

        log.debug("Completed periodic reconciliation");
    }
}
