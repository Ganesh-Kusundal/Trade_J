package com.tradej.app.pipeline.reactor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.reactor.ReactorBridge;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscribes to {@link ReactorBridge#events()} and dispatches to metrics plus optional custom consumers.
 */
@Component
public final class ReactorColdPathRegistry {

    private static final Logger log = LoggerFactory.getLogger(ReactorColdPathRegistry.class);

    private final ReactorBridge reactorBridge;
    private final ReactorBridgeMetrics metrics;
    private final List<ReactorColdPathConsumer> consumers;
    private final List<Disposable> subscriptions = new ArrayList<>();

    public ReactorColdPathRegistry(
            ReactorBridge reactorBridge,
            ReactorBridgeMetrics metrics,
            List<ReactorColdPathConsumer> consumers
    ) {
        this.reactorBridge = reactorBridge;
        this.metrics = metrics;
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
        start();
    }

    private void start() {
        subscriptions.add(reactorBridge.events().subscribe(this::dispatch));
        log.info("Reactor cold-path registry started customConsumers={}", this.consumers.size());
    }

    private void dispatch(DomainEvent event) {
        metrics.record(event);
        for (ReactorColdPathConsumer consumer : consumers) {
            try {
                consumer.onEvent(event);
            } catch (Exception e) {
                log.warn("Reactor cold-path consumer failed for eventType={}: {}",
                        event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
    }
}
