package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Handles {@link PositionMismatch} events published by the reconciliation pipeline.
 */
@Service
public class ReconciliationAlertLogger implements DomainEventHandler<PositionMismatch> {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationAlertLogger.class);

    private final EventBus eventBus;
    private final boolean autoHalt;
    private final long mismatchToleranceQty;

    public ReconciliationAlertLogger(
            EventBus eventBus,
            @Value("${trade.reconciliation.auto-halt:false}") boolean autoHalt,
            @Value("${trade.reconciliation.mismatch-tolerance-qty:0}") long mismatchToleranceQty
    ) {
        this.eventBus = eventBus;
        this.autoHalt = autoHalt;
        this.mismatchToleranceQty = mismatchToleranceQty;
    }

    @Override
    public void onEvent(PositionMismatch event) {
        long mismatch = Math.abs(event.paperQuantity() - event.brokerQuantity());
        log.warn(
                "Position mismatch detected [engineKey={}] symbol={} expectedQuantity={} brokerQuantity={} mismatch={} eventId={}",
                event.engineKey(),
                event.symbol(),
                event.paperQuantity(),
                event.brokerQuantity(),
                mismatch,
                event.metadata().eventId());
        if (autoHalt && mismatch > mismatchToleranceQty && eventBus != null) {
            eventBus.publish(new ReconciliationHaltRequired(
                    EventMetadata.root(),
                    event.symbol(),
                    event.paperQuantity(),
                    event.brokerQuantity(),
                    event.engineKey(),
                    mismatch));
        }
    }
}
