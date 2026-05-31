package com.tradej.execution.identity;

import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderEvent;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Rebuilds the in-memory {@link OrderIdentityRegistry} from persisted OMS events
 * on startup so post-restart fills can resolve broker order IDs.
 */
public final class OrderIdentityRehydrator {

    private static final Logger log = LoggerFactory.getLogger(OrderIdentityRehydrator.class);

    private OrderIdentityRehydrator() {
    }

    /**
     * Scans all known OMS aggregates and registers signal/broker identity mappings.
     *
     * @return number of internal order IDs rehydrated
     */
    public static int rehydrate(EventSourcedOrderRepository omsRepo, OrderIdentityRegistry registry) {
        int count = 0;
        for (String internalOrderId : omsRepo.knownOrderIds()) {
            List<OrderEvent> events = omsRepo.orderEvents(internalOrderId);
            if (events.isEmpty()) {
                continue;
            }
            String signalId = null;
            String brokerOrderId = null;
            for (OrderEvent event : events) {
                if (event instanceof OrderSubmitted submitted) {
                    signalId = submitted.correlationId();
                } else if (event instanceof OrderAcknowledged acknowledged) {
                    brokerOrderId = acknowledged.exchangeOrderId();
                }
            }
            if ((signalId != null && !signalId.isBlank()) || (brokerOrderId != null && !brokerOrderId.isBlank())) {
                registry.register(internalOrderId, brokerOrderId, signalId);
                count++;
            }
        }
        log.info("Rehydrated {} order identity mappings from OMS journal", count);
        return count;
    }
}
