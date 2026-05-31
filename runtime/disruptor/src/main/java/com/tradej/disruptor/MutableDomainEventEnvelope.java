package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;

public final class MutableDomainEventEnvelope {
    private DomainEvent event;

    public DomainEvent event() {
        return event;
    }

    public void setEvent(DomainEvent event) {
        this.event = event;
    }

    public void clear() {
        this.event = null;
    }
}
