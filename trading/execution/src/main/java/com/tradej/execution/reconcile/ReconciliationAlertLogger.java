package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.port.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles {@link PositionMismatch} events published by the reconciliation pipeline.
 *
 * <p>Logs each mismatch at WARN level with structured details (symbol, expected quantity,
 * broker quantity, engine key). Designed as a single extension point for future
 * alerting channels (Slack, email, PagerDuty, etc.).
 *
 * <p>Registered as a subscriber on the {@link com.tradej.core.domain.port.EventBus}
 * during application startup for {@code PositionMismatch.class}.
 */
@Service
public class ReconciliationAlertLogger implements DomainEventHandler<PositionMismatch> {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationAlertLogger.class);

    @Override
    public void onEvent(PositionMismatch event) {
        log.warn(
                "Position mismatch detected [engineKey={}] symbol={} expectedQuantity={} brokerQuantity={} eventId={}",
                event.engineKey(),
                event.symbol(),
                event.paperQuantity(),
                event.brokerQuantity(),
                event.metadata().eventId()
        );
    }
}
