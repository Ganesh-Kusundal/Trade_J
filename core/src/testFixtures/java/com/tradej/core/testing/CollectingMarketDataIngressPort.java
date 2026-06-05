package com.tradej.core.testing;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.port.MarketDataIngressPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test fixture that captures all market data events published through
 * {@link MarketDataIngressPort} without requiring a live Disruptor or broker.
 *
 * <p>Use this in component tests to verify that broker adapters produce
 * the correct normalized domain events.
 */
public final class CollectingMarketDataIngressPort implements MarketDataIngressPort {

    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publishMarketTick(MarketTickEvent event) {
        events.add(event);
    }

    @Override
    public void publishDepthUpdate(DepthUpdateEvent event) {
        events.add(event);
    }

    @Override
    public void publishHealth(StreamHealthChanged event) {
        events.add(event);
    }

    public List<DomainEvent> events() {
        return List.copyOf(events);
    }

    public List<MarketTickEvent> ticks() {
        return events.stream()
                .filter(MarketTickEvent.class::isInstance)
                .map(MarketTickEvent.class::cast)
                .toList();
    }

    public List<DepthUpdateEvent> depthUpdates() {
        return events.stream()
                .filter(DepthUpdateEvent.class::isInstance)
                .map(DepthUpdateEvent.class::cast)
                .toList();
    }

    public void clear() {
        events.clear();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public int size() {
        return events.size();
    }
}
