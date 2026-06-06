package com.tradej.broker.core.depth;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class EventBusDepthBridgeTest {

    private static DepthUpdateEvent depthEvent(String symbol, ExchangeSegment segment,
                                                List<DepthLevel> bids, List<DepthLevel> asks) {
        return new DepthUpdateEvent(
                new EventMetadata("test", System.currentTimeMillis(), 0, 0, "", 1),
                symbol, segment, bids, asks, bids.size(), System.currentTimeMillis());
    }

    private static final class DispatchingEventBus implements EventBus {
        private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<DomainEventHandler<?>>> handlers =
                new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add((DomainEventHandler) handler);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            var list = handlers.get(eventType);
            if (list != null) list.remove((DomainEventHandler) handler);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void publish(DomainEvent event) {
            var list = handlers.get(event.getClass());
            if (list == null) return;
            for (var h : list) {
                try { ((DomainEventHandler) h).onEvent(event); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }
        }

        @Override public void start() {}
        @Override public void stop() {}

        int handlerCount(Class<?> type) {
            var list = handlers.get(type);
            return list == null ? 0 : list.size();
        }
    }

    @Test
    void bridgeForwardsDepthUpdatesToEngine() {
        OrderBookEngine engine = new OrderBookEngine();
        DispatchingEventBus bus = new DispatchingEventBus();
        EventBusDepthBridge bridge = new EventBusDepthBridge(engine, bus);
        bridge.start();

        bus.publish(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)),
                List.of(new DepthLevel(250100, 50, 1))));

        assertEquals(1, engine.bookCount());
        assertEquals(250000, engine.getBook("RELIANCE", ExchangeSegment.NSE_EQ).bestBidPaisa());
    }

    @Test
    void bridgeStopUnsubscribesHandler() {
        OrderBookEngine engine = new OrderBookEngine();
        DispatchingEventBus bus = new DispatchingEventBus();
        EventBusDepthBridge bridge = new EventBusDepthBridge(engine, bus);
        bridge.start();
        int sizeBeforeStop = bus.handlerCount(DepthUpdateEvent.class);
        bridge.stop();
        int sizeAfterStop = bus.handlerCount(DepthUpdateEvent.class);
        assertEquals(1, sizeBeforeStop, "handler should be registered after start");
        assertEquals(0, sizeAfterStop, "handler should be removed after stop");

        bus.publish(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)),
                List.of()));

        assertEquals(0, engine.bookCount());
    }

    @Test
    void bridgeFanOutsAcrossMultipleBooks() {
        OrderBookEngine engine = new OrderBookEngine();
        DispatchingEventBus bus = new DispatchingEventBus();
        EventBusDepthBridge bridge = new EventBusDepthBridge(engine, bus);
        bridge.start();

        bus.publish(depthEvent("RELIANCE", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(250000, 100, 1)),
                List.of()));
        bus.publish(depthEvent("NIFTY", ExchangeSegment.IDX_I,
                List.of(new DepthLevel(24000_00, 50, 1)),
                List.of()));
        bus.publish(depthEvent("TCS", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(380000, 80, 1)),
                List.of()));

        assertEquals(3, engine.bookCount());
        assertEquals(250000, engine.getBook("RELIANCE", ExchangeSegment.NSE_EQ).bestBidPaisa());
        assertEquals(24000_00, engine.getBook("NIFTY", ExchangeSegment.IDX_I).bestBidPaisa());
        assertEquals(380000, engine.getBook("TCS", ExchangeSegment.NSE_EQ).bestBidPaisa());
    }
}
