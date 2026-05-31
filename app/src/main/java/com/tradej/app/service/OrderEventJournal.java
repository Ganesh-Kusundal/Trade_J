package com.tradej.app.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderCancelled;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderModified;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.OrderFullyFilled;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OrderEventJournal {

    public sealed interface OrderEventEntry permits
            Accepted, PartiallyFilled, FullyFilled, Modified, Cancelled, Rejected {
        String orderId();
        Instant timestamp();
        <T> T unwrap();
    }

    public record Accepted(OrderAccepted accepted, Instant timestamp) implements OrderEventEntry {
        @Override public String orderId() { return accepted.order().orderId(); }
        @Override public Instant timestamp() { return timestamp; }
        @Override public <T> T unwrap() { return (T) accepted; }
    }
    public record PartiallyFilled(OrderPartiallyFilled filled, Instant timestamp) implements OrderEventEntry {
        @Override public String orderId() { return filled.order().orderId(); }
        @Override public Instant timestamp() { return timestamp; }
        @Override public <T> T unwrap() { return (T) filled; }
    }
    public record FullyFilled(OrderFullyFilled filled, Instant timestamp) implements OrderEventEntry {
        @Override public String orderId() { return filled.order().orderId(); }
        @Override public Instant timestamp() { return timestamp; }
        @Override public <T> T unwrap() { return (T) filled; }
    }
    public record Modified(OrderModified modified, Instant timestamp) implements OrderEventEntry {
        @Override public String orderId() { return modified.order().orderId(); }
        @Override public Instant timestamp() { return timestamp; }
        @Override public <T> T unwrap() { return (T) modified; }
    }
    public record Cancelled(OrderCancelled cancelled, Instant timestamp) implements OrderEventEntry {
        @Override public String orderId() { return cancelled.order().orderId(); }
        @Override public Instant timestamp() { return timestamp; }
        @Override public <T> T unwrap() { return (T) cancelled; }
    }
    public record Rejected(OrderRejected rejected, Instant timestamp) implements OrderEventEntry {
        @Override public String orderId() { return rejected.order().orderId(); }
        @Override public Instant timestamp() { return timestamp; }
        @Override public <T> T unwrap() { return (T) rejected; }
    }

    private final Map<String, List<OrderEventEntry>> journal = new ConcurrentHashMap<>();

    private static Instant timestampFrom(DomainEvent event) {
        return Instant.ofEpochMilli(event.metadata().timestampMs());
    }

    public void onDomainEvent(DomainEvent event) {
        OrderEventEntry entry = switch (event) {
            case OrderAccepted accepted -> new Accepted(accepted, timestampFrom(event));
            case OrderPartiallyFilled filled -> new PartiallyFilled(filled, timestampFrom(event));
            case OrderFullyFilled filled -> new FullyFilled(filled, timestampFrom(event));
            case OrderModified modified -> new Modified(modified, timestampFrom(event));
            case OrderCancelled cancelled -> new Cancelled(cancelled, timestampFrom(event));
            case OrderRejected rejected -> new Rejected(rejected, timestampFrom(event));
            default -> null;
        };

        if (entry != null) {
            journal.computeIfAbsent(entry.orderId(), k -> new ArrayList<>()).add(entry);
        }
    }

    public List<OrderEventEntry> getEvents(String orderId) {
        return Collections.unmodifiableList(
                new ArrayList<>(journal.getOrDefault(orderId, List.of()))
        );
    }

    public OrderEventEntry getLastEvent(String orderId) {
        var events = journal.get(orderId);
        return events == null || events.isEmpty() ? null : events.get(events.size() - 1);
    }

    public List<String> getOrderIds() {
        return List.copyOf(journal.keySet());
    }
}