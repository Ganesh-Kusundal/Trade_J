package com.tradej.execution.subscription;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.value.FeedMode;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Facade over {@link SubscriptionCoordinator} with wire-state visibility for recovery.
 */
public final class SubscriptionManager {

    private final SubscriptionCoordinator coordinator;
    private final WebSocketMultiplexer websocket;

    public SubscriptionManager(SubscriptionCoordinator coordinator, WebSocketMultiplexer websocket) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.websocket = Objects.requireNonNull(websocket, "websocket");
    }

    public void subscribe(List<MarketSubscriptionRequest> requests, FeedMode feedMode) {
        coordinator.subscribe(requests, feedMode);
    }

    public void reconcileAfterReconnect() {
        coordinator.reconcileAfterReconnect();
    }

    public SubscriptionSnapshot snapshot() {
        EnumMap<FeedMode, Set<MarketSubscriptionRequest>> merged = new EnumMap<>(FeedMode.class);
        coordinator.desiredSnapshot().forEach((mode, requests) ->
                merged.computeIfAbsent(mode, ignored -> new LinkedHashSet<>()).addAll(requests));
        for (Map.Entry<MarketSubscriptionRequest, FeedMode> entry : websocket.subscriptions().entrySet()) {
            merged.computeIfAbsent(entry.getValue(), ignored -> new LinkedHashSet<>()).add(entry.getKey());
        }
        EnumMap<FeedMode, Set<MarketSubscriptionRequest>> frozen = new EnumMap<>(FeedMode.class);
        merged.forEach((mode, requests) -> frozen.put(mode, Set.copyOf(requests)));
        return new SubscriptionSnapshot(Map.copyOf(frozen));
    }

    public record SubscriptionSnapshot(Map<FeedMode, Set<MarketSubscriptionRequest>> merged) {
        public SubscriptionSnapshot {
            merged = merged == null ? Map.of() : Map.copyOf(merged);
        }
    }
}
