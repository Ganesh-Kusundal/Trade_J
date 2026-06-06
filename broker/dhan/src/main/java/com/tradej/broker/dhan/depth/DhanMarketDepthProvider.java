package com.tradej.broker.dhan.depth;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Dhan broker's market depth provider — backed by the shared {@link OrderBookEngine}.
 *
 * <p>This provider exposes:
 * <ul>
 *   <li>Active book keys for the twenty-depth WebSocket bridge</li>
 *   <li>Snapshot retrieval for the broker gateway REST surface</li>
 *   <li>A listener fan-out used by tests and analytics consumers</li>
 * </ul>
 *
 * <p>Lifecycle: callers must invoke {@link #start()} once before subscribing
 * to {@link #addDepthListener(Consumer)} listeners, and {@link #stop()} on
 * shutdown. The Spring wiring lives in {@code app}'s
 * {@code com.tradej.app.config.DhanDepthSpringConfig} (broker-dhan stays
 * Spring-free).
 */
public final class DhanMarketDepthProvider {

    private final OrderBookEngine engine;
    private final Set<String> subscribedKeys = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<DepthUpdateEvent>> depthListeners =
            new CopyOnWriteArrayList<>();
    private volatile Consumer<DepthUpdateEvent> engineListener;

    public DhanMarketDepthProvider(OrderBookEngine engine) {
        this.engine = engine;
    }

    public void start() {
        if (engineListener != null) {
            return;
        }
        engineListener = event -> {
            String key = bookKey(event.symbol(), event.segment());
            if (!subscribedKeys.contains(key)) {
                return;
            }
            for (Consumer<DepthUpdateEvent> listener : depthListeners) {
                try {
                    listener.accept(event);
                } catch (RuntimeException ex) {
                    // listener failures must not block the engine
                }
            }
        };
        engine.addListener(engineListener);
    }

    public void stop() {
        if (engineListener != null) {
            engine.removeListener(engineListener);
            engineListener = null;
        }
        subscribedKeys.clear();
        depthListeners.clear();
    }

    public void subscribe(String symbol, ExchangeSegment segment) {
        subscribedKeys.add(bookKey(symbol, segment));
    }

    public void unsubscribe(String symbol, ExchangeSegment segment) {
        subscribedKeys.remove(bookKey(symbol, segment));
    }

    public boolean isSubscribed(String symbol, ExchangeSegment segment) {
        return subscribedKeys.contains(bookKey(symbol, segment));
    }

    public int subscriptionCount() {
        return subscribedKeys.size();
    }

    public void addDepthListener(Consumer<DepthUpdateEvent> listener) {
        depthListeners.add(listener);
    }

    public void removeDepthListener(Consumer<DepthUpdateEvent> listener) {
        depthListeners.remove(listener);
    }

    public OrderBook.OrderBookSnapshot snapshot(String symbol, ExchangeSegment segment, int levels) {
        OrderBook book = engine.getBook(symbol, segment);
        if (book == null) {
            return null;
        }
        return book.toSnapshot(levels);
    }

    public List<OrderBook.OrderBookSnapshot> snapshotsAll(int levels) {
        return engine.allBooks().values().stream()
                .map(book -> book.toSnapshot(levels))
                .toList();
    }

    public Map<String, ExchangeSegment> activeBooks() {
        Map<String, ExchangeSegment> active = new java.util.LinkedHashMap<>();
        for (OrderBook book : engine.allBooks().values()) {
            active.put(book.symbol(), book.segment());
        }
        return active;
    }

    public int bookCount() {
        return engine.bookCount();
    }

    public static FeedMode depthFeedMode() {
        return FeedMode.DEPTH_20;
    }

    private static String bookKey(String symbol, ExchangeSegment segment) {
        return segment.name() + "::" + symbol;
    }
}
