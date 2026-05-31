package com.tradej.broker.api.port;

import com.tradej.core.domain.event.DomainEvent;

@FunctionalInterface
public interface OrderUpdateListener {
    void onEvent(DomainEvent event);
}
