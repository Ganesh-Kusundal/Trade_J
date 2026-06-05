package com.tradej.app.subscription;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.value.FeedMode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks desired WebSocket subscriptions and reconciles subscribe/unsubscribe diffs.
 */
public final class SubscriptionCoordinator {

    private final WebSocketMultiplexer websocket;
    private final int batchSize;
    private final EnumMap<FeedMode, Set<MarketSubscriptionRequest>> desiredByFeedMode =
            new EnumMap<>(FeedMode.class);

    public SubscriptionCoordinator(WebSocketMultiplexer websocket, int batchSize) {
        this.websocket = Objects.requireNonNull(websocket, "websocket");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public void subscribe(List<MarketSubscriptionRequest> requests, FeedMode feedMode) {
        Objects.requireNonNull(feedMode, "feedMode");
        if (requests == null || requests.isEmpty()) {
            return;
        }
        Set<MarketSubscriptionRequest> desired = desiredFor(feedMode);
        List<MarketSubscriptionRequest> toSubscribe = new ArrayList<>();
        for (MarketSubscriptionRequest request : requests) {
            if (desired.add(request)) {
                toSubscribe.add(request);
            }
        }
        subscribeBatched(toSubscribe, feedMode);
    }

    public void replaceSubscriptions(Map<FeedMode, Set<MarketSubscriptionRequest>> nextDesired) {
        Objects.requireNonNull(nextDesired, "nextDesired");
        for (FeedMode mode : FeedMode.values()) {
            Set<MarketSubscriptionRequest> next = nextDesired.getOrDefault(mode, Set.of());
            Set<MarketSubscriptionRequest> current = desiredFor(mode);
            Set<MarketSubscriptionRequest> toRemove = new LinkedHashSet<>(current);
            toRemove.removeAll(next);
            Set<MarketSubscriptionRequest> toAdd = new LinkedHashSet<>(next);
            toAdd.removeAll(current);
            if (!toRemove.isEmpty()) {
                unsubscribeBatched(List.copyOf(toRemove));
                current.removeAll(toRemove);
            }
            if (!toAdd.isEmpty()) {
                current.addAll(toAdd);
                subscribeBatched(List.copyOf(toAdd), mode);
            }
        }
    }

    public void reconcileAfterReconnect() {
        for (Map.Entry<FeedMode, Set<MarketSubscriptionRequest>> entry : desiredByFeedMode.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                subscribeBatched(List.copyOf(entry.getValue()), entry.getKey());
            }
        }
    }

    public Map<FeedMode, Integer> countsByFeedMode() {
        EnumMap<FeedMode, Integer> counts = new EnumMap<>(FeedMode.class);
        for (Map.Entry<FeedMode, Set<MarketSubscriptionRequest>> entry : desiredByFeedMode.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return Map.copyOf(counts);
    }

    Map<FeedMode, Set<MarketSubscriptionRequest>> desiredSnapshot() {
        EnumMap<FeedMode, Set<MarketSubscriptionRequest>> copy = new EnumMap<>(FeedMode.class);
        for (Map.Entry<FeedMode, Set<MarketSubscriptionRequest>> entry : desiredByFeedMode.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(copy);
    }

    private Set<MarketSubscriptionRequest> desiredFor(FeedMode feedMode) {
        return desiredByFeedMode.computeIfAbsent(feedMode, ignored -> new LinkedHashSet<>());
    }

    private void subscribeBatched(List<MarketSubscriptionRequest> requests, FeedMode feedMode) {
        for (int index = 0; index < requests.size(); index += batchSize) {
            int end = Math.min(index + batchSize, requests.size());
            websocket.subscribe(requests.subList(index, end), feedMode);
        }
    }

    private void unsubscribeBatched(List<MarketSubscriptionRequest> requests) {
        for (int index = 0; index < requests.size(); index += batchSize) {
            int end = Math.min(index + batchSize, requests.size());
            websocket.unsubscribe(requests.subList(index, end));
        }
    }
}
