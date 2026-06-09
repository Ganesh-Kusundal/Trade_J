package com.tradej.core.domain.event;

import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe, synchronous {@link EventBus} implementation suitable as the
 * default platform event bus.
 *
 * <p>Events are dispatched to all matching subscribers on the caller's thread.
 * This is the correct default for most use cases; the
 * {@link com.tradej.disruptor.DisruptorEventBus} exists for hot-path scenarios
 * that require ring-buffer throughput.
 *
 * <p>Subscriber exceptions are caught and logged so one failing handler
 * cannot prevent other handlers from receiving the event.
 */
public final class SimpleEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(SimpleEventBus.class);

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers =
            new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        List<DomainEventHandler<? extends DomainEvent>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        // Dispatch to exact-type subscribers
        List<DomainEventHandler<? extends DomainEvent>> exact = subscribers.get(event.getClass());
        if (exact != null) {
            for (DomainEventHandler<? extends DomainEvent> handler : exact) {
                dispatch(handler, event);
            }
        }
        // Dispatch to DomainEvent.class subscribers (catch-all)
        if (event.getClass() != DomainEvent.class) {
            List<DomainEventHandler<? extends DomainEvent>> catchAll = subscribers.get(DomainEvent.class);
            if (catchAll != null) {
                for (DomainEventHandler<? extends DomainEvent> handler : catchAll) {
                    dispatch(handler, event);
                }
            }
        }
    }

    @Override
    public void start() {
        started.set(true);
        log.info("SimpleEventBus started ({} event types registered)", subscribers.size());
    }

    @Override
    public void stop() {
        started.set(false);
        log.info("SimpleEventBus stopped");
    }

    public boolean isStarted() {
        return started.get();
    }

    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    public int eventTypeCount() {
        return subscribers.size();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(DomainEventHandler handler, DomainEvent event) {
        try {
            handler.onEvent(event);
        } catch (Exception e) {
            log.error("Event handler {} failed for event type={}: {}",
                    handler.getClass().getSimpleName(),
                    event.getClass().getSimpleName(),
                    e.getMessage(), e);
        }
    }
}
