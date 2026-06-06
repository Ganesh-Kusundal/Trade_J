package com.tradej.broker.core.depth;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages per-symbol OrderBook instances, updated from DepthUpdateEvent stream.
 * Consumers (analytics services, REST endpoints) read from this engine.
 * Thread-safe.
 */
public final class OrderBookEngine {

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final java.util.concurrent.CopyOnWriteArrayList<Consumer<DepthUpdateEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Process a depth update event. Creates the book if new, applies the update.
     */
    public void onDepthUpdate(DepthUpdateEvent event) {
        String key = bookKey(event.symbol(), event.segment());
        OrderBook book = books.computeIfAbsent(key, k -> new OrderBook(event.symbol(), event.segment()));
        book.update(event.bids(), event.asks());
        for (Consumer<DepthUpdateEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
                // Analytics listener failure should not block other consumers
            }
        }
    }

    /**
     * Register a listener that is notified on every depth update (after book is updated).
     */
    public void addListener(Consumer<DepthUpdateEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<DepthUpdateEvent> listener) {
        listeners.remove(listener);
    }

    public OrderBook getBook(String symbol, ExchangeSegment segment) {
        return books.get(bookKey(symbol, segment));
    }

    public OrderBook getOrCreateBook(String symbol, ExchangeSegment segment) {
        return books.computeIfAbsent(bookKey(symbol, segment), k -> new OrderBook(symbol, segment));
    }

    public Map<String, OrderBook> allBooks() {
        return Map.copyOf(books);
    }

    public int bookCount() {
        return books.size();
    }

    private static String bookKey(String symbol, ExchangeSegment segment) {
        return segment.name() + "::" + symbol;
    }
}
