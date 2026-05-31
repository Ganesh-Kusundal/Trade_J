package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DomainEvent;

@FunctionalInterface
public interface DomainEventHandler<T extends DomainEvent> {
    void onEvent(T event) throws Exception;
}
