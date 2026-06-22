package com.tradej.execution.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically triggers the complete broker position reconciliation use case.
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
 * <p>Reconciliation mismatches (if any) are published by {@link ReconciliationUseCase}
 * so they can be logged, alerted on, or trigger corrective actions.
 */
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationUseCase reconciliationUseCase;

    public ReconciliationScheduler(ReconciliationUseCase reconciliationUseCase) {
        this.reconciliationUseCase = reconciliationUseCase;
    }

    /**
     * Runs both expected-vs-broker and OSM reconciliation on a fixed schedule.
     *
     * <p>Publishes all {@link com.tradej.core.domain.event.PositionMismatch PositionMismatch}
     * events through the event bus for downstream alerting/logging.
     */
    public void reconcilePeriodically() {
        log.debug("Starting periodic reconciliation");
        reconciliationUseCase.reconcileAllSources();
        log.debug("Completed periodic reconciliation");
    }
}
