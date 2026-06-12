package com.tradej.benchmark;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VirtualThreadEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadEventBus.class);

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers =
            new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile ExecutorService executor;

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
        if (event == null || !started.get() || executor == null) {
            return;
        }

        // Dispatch to exact-type subscribers
        List<DomainEventHandler<? extends DomainEvent>> exact = subscribers.get(event.getClass());
        if (exact != null) {
            for (DomainEventHandler<? extends DomainEvent> handler : exact) {
                executor.submit(() -> dispatch(handler, event));
            }
        }

        // Dispatch to DomainEvent.class subscribers (catch-all)
        if (event.getClass() != DomainEvent.class) {
            List<DomainEventHandler<? extends DomainEvent>> catchAll = subscribers.get(DomainEvent.class);
            if (catchAll != null) {
                for (DomainEventHandler<? extends DomainEvent> handler : catchAll) {
                    executor.submit(() -> dispatch(handler, event));
                }
            }
        }
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            log.info("VirtualThreadEventBus started");
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("VirtualThreadEventBus stopped");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(DomainEventHandler handler, DomainEvent event) {
        try {
            handler.onEvent(event);
        } catch (Exception e) {
            log.error("VirtualThreadEventBus handler {} failed for event type={}: {}",
                    handler.getClass().getSimpleName(),
                    event.getClass().getSimpleName(),
                    e.getMessage(), e);
        }
    }
}
