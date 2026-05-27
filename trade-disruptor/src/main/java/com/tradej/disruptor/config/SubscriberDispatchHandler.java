package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.disruptor.MutableDomainEventEnvelope;

import java.util.List;
import java.util.Map;

public final class SubscriberDispatchHandler implements EventHandler<MutableDomainEventEnvelope> {
    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers;

    public SubscriberDispatchHandler(Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers) {
        this.subscribers = subscribers;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onEvent(MutableDomainEventEnvelope envelope, long sequence, boolean endOfBatch) throws Exception {
        DomainEvent event = envelope.event();
        if (event == null) {
            return;
        }
        for (Map.Entry<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> entry : subscribers.entrySet()) {
            if (entry.getKey().isAssignableFrom(event.getClass())) {
                for (DomainEventHandler handler : entry.getValue()) {
                    handler.onEvent(event);
                }
            }
        }
        envelope.clear();
    }
}
