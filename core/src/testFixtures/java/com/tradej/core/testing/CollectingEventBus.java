package com.tradej.core.testing;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canonical in-memory {@link EventBus} implementation for testing.
 * Captures all published events for assertions.
 * <p>
 * Thread-safe: backed by {@link CopyOnWriteArrayList} and {@link ConcurrentHashMap}.
 * <p>
 * Replaces duplicate CollectingEventBus implementations in trade-app tests.
 */
public final class CollectingEventBus implements EventBus {

    private final CopyOnWriteArrayList<DomainEvent> allEvents = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<?>> eventsByType = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DomainEventHandler<?>> handlers = new CopyOnWriteArrayList<>();
    private volatile boolean started = false;

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        handlers.add(handler);
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        handlers.remove(handler);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(DomainEvent event) {
        allEvents.add(event);
        ((CopyOnWriteArrayList) eventsByType.computeIfAbsent(event.getClass(), k -> new CopyOnWriteArrayList<>())).add(event);
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void stop() {
        started = false;
    }

    /**
     * Returns all published events in order.
     */
    public List<DomainEvent> allEvents() {
        return List.copyOf(allEvents);
    }

    /**
     * Returns all events of the given type in order.
     */
    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> List<T> eventsOf(Class<T> type) {
        return (List<T>) eventsByType.getOrDefault(type, new CopyOnWriteArrayList<>());
    }

    /**
     * Returns the first event of the given type, or throws if none found.
     */
    public <T extends DomainEvent> T firstEventOf(Class<T> type) {
        List<T> events = eventsOf(type);
        if (events.isEmpty()) {
            throw new AssertionError("No events of type " + type.getSimpleName() + " were published");
        }
        return events.getFirst();
    }

    /**
     * Returns the last event of the given type, or throws if none found.
     */
    public <T extends DomainEvent> T lastEventOf(Class<T> type) {
        List<T> events = eventsOf(type);
        if (events.isEmpty()) {
            throw new AssertionError("No events of type " + type.getSimpleName() + " were published");
        }
        return events.getLast();
    }

    /**
     * Returns the count of events of the given type.
     */
    public <T extends DomainEvent> int countOf(Class<T> type) {
        return eventsOf(type).size();
    }

    /**
     * Returns the total count of all published events.
     */
    public int totalCount() {
        return allEvents.size();
    }

    /**
     * Asserts that no events of the given type were published.
     */
    public <T extends DomainEvent> void assertNoEventsOf(Class<T> type) {
        if (countOf(type) > 0) {
            throw new AssertionError(
                    "Expected no events of type " + type.getSimpleName()
                            + " but found " + countOf(type));
        }
    }

    /**
     * Asserts that at least one event of the given type was published.
     */
    public <T extends DomainEvent> void assertHasEventOf(Class<T> type) {
        if (countOf(type) == 0) {
            throw new AssertionError(
                    "Expected at least one event of type " + type.getSimpleName());
        }
    }

    /**
     * Clears all captured events.
     */
    public void clear() {
        allEvents.clear();
        eventsByType.clear();
    }

    /**
     * Returns true if this bus has been started.
     */
    public boolean isStarted() {
        return started;
    }
}
