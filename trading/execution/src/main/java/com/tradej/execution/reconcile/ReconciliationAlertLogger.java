package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.reconcile.ReconciliationDecision;
import com.tradej.core.domain.reconcile.ReconciliationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles {@link PositionMismatch} events published by the reconciliation pipeline.
 */
public class ReconciliationAlertLogger implements DomainEventHandler<PositionMismatch> {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationAlertLogger.class);

    private final EventBus eventBus;
    private final ReconciliationPolicy policy;

    public ReconciliationAlertLogger(
            EventBus eventBus,
            ReconciliationPolicy policy
    ) {
        this.eventBus = eventBus;
        this.policy = policy;
    }

    @Override
    public void onEvent(PositionMismatch event) {
        ReconciliationDecision decision = policy.evaluate(event);
        log.warn(
                "Position mismatch detected [engineKey={}] symbol={} expectedQuantity={} brokerQuantity={} mismatch={} haltRequired={} eventId={}",
                event.engineKey(),
                event.symbol(),
                event.paperQuantity(),
                event.brokerQuantity(),
                decision.mismatchQuantity(),
                decision.haltRequired(),
                event.metadata().eventId());
        if (decision.haltRequired() && eventBus != null) {
            eventBus.publish(new ReconciliationHaltRequired(
                    EventMetadata.root(),
                    event.symbol(),
                    event.paperQuantity(),
                    event.brokerQuantity(),
                    event.engineKey(),
                    decision.mismatchQuantity(),
                    decision.reason()));
        }
    }
}
