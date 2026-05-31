package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DomainEvent;

import java.util.List;

public interface EventBus {
    <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler);

    <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler);

    void publish(DomainEvent event);

    default void publishBatch(List<? extends DomainEvent> events) {
        events.forEach(this::publish);
    }

    void start();

    void stop();
}
