package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DomainEvent;

import java.util.function.Consumer;

/**
 * Port for write-ahead logging of domain events before they enter the event bus.
 *
 * <p>Implementations persist events to durable storage so that on crash recovery,
 * events that were persisted but not yet processed can be replayed.
 *
 * <p>The no-op implementation ({@link #noop()}) disables WAL entirely.
 */
public interface EventWriteAheadLog {

    /**
     * Write an event to the WAL. Must complete before the event enters the ring buffer.
     *
     * @param event the domain event to persist
     */
    void write(DomainEvent event);

    /**
     * Replay all events from the WAL, passing each to the given consumer.
     *
     * @param consumer receives each replayed event
     * @return number of events replayed
     */
    long replay(Consumer<DomainEvent> consumer);

    /**
     * Close the WAL and release resources.
     */
    default void close() {}

    /**
     * No-op implementation that does nothing.
     */
    static EventWriteAheadLog noop() {
        return new EventWriteAheadLog() {
            @Override public void write(DomainEvent event) {}
            @Override public long replay(Consumer<DomainEvent> consumer) { return 0; }
        };
    }
}
