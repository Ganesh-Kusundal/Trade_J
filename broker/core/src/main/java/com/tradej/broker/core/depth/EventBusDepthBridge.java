package com.tradej.broker.core.depth;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;

/**
 * Wires the {@link OrderBookEngine} into the application {@link EventBus} so that
 * every {@link DepthUpdateEvent} published on the bus updates the engine's books.
 *
 * <p>This is the single seam between broker WebSocket depth feeds and the
 * per-symbol in-memory order book state consumed by REST controllers and the
 * depth analytics pipeline.
 */
public final class EventBusDepthBridge {

    private final OrderBookEngine engine;
    private final EventBus eventBus;
    private final DomainEventHandler<DepthUpdateEvent> handler = this::onDepthUpdate;

    public EventBusDepthBridge(OrderBookEngine engine, EventBus eventBus) {
        this.engine = engine;
        this.eventBus = eventBus;
    }

    public void start() {
        eventBus.subscribe(DepthUpdateEvent.class, handler);
    }

    public void stop() {
        eventBus.unsubscribe(DepthUpdateEvent.class, handler);
    }

    private void onDepthUpdate(DepthUpdateEvent event) {
        try {
            engine.onDepthUpdate(event);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "OrderBookEngine failed to apply depth update for "
                            + event.symbol() + "/" + event.segment(), ex);
        }
    }
}
