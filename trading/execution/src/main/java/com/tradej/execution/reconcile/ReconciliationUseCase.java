package com.tradej.execution.reconcile;

import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application use case for running the complete position reconciliation contract.
 */
public final class ReconciliationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationUseCase.class);

    private final OrderReconciler orderReconciler;
    private final EventBus eventBus;
    private final NetPositionProvider netPositionProvider;

    public ReconciliationUseCase(
            OrderReconciler orderReconciler,
            EventBus eventBus,
            NetPositionProvider netPositionProvider
    ) {
        this.orderReconciler = orderReconciler;
        this.eventBus = eventBus;
        this.netPositionProvider = netPositionProvider;
    }

    public void reconcileAllSources() {
        reconcileExpectedNetPositions();
        reconcileOmsProjection();
    }

    public void reconcileOmsProjection() {
        try {
            orderReconciler.reconcileAll(eventBus::publish);
        } catch (Exception e) {
            log.error("OSM-vs-broker reconciliation failed", e);
        }
    }

    private void reconcileExpectedNetPositions() {
        try {
            orderReconciler.reconcile(netPositionProvider.getNetPositions(), eventBus::publish);
        } catch (Exception e) {
            log.error("Expected-vs-broker reconciliation failed", e);
        }
    }
}
