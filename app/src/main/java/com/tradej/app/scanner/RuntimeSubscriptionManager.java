package com.tradej.app.scanner;

import com.tradej.app.config.TradingProperties;
import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.scanner.model.PromotionSpec;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.scanner.model.ScanProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class RuntimeSubscriptionManager {
    private static final Logger log = LoggerFactory.getLogger(RuntimeSubscriptionManager.class);

    private final WebSocketMultiplexer websocket;
    private final SubscriptionCoordinator coordinator;
    private final List<TradingProperties.SubscriptionProperties> staticSubscriptions;
    private final int batchSize;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<MarketSubscriptionRequest, Instant> promotedAt = new LinkedHashMap<>();

    public RuntimeSubscriptionManager(
            WebSocketMultiplexer websocket,
            TradingProperties properties
    ) {
        this(websocket, null, properties);
    }

    public RuntimeSubscriptionManager(
            WebSocketMultiplexer websocket,
            SubscriptionCoordinator coordinator,
            TradingProperties properties
    ) {
        this.websocket = websocket;
        this.coordinator = coordinator;
        this.staticSubscriptions = properties.subscriptions() == null ? List.of() : properties.subscriptions();
        int configured = properties.universe() == null ? 0 : properties.universe().maxSubscriptionsPerBatch();
        this.batchSize = configured > 0 ? configured : 50;
    }

    public void subscribeStaticAtStartup() {
        if (staticSubscriptions.isEmpty()) {
            return;
        }
        Map<FeedMode, List<MarketSubscriptionRequest>> byFeed = new LinkedHashMap<>();
        for (TradingProperties.SubscriptionProperties sub : staticSubscriptions) {
            MarketSubscriptionRequest request = new MarketSubscriptionRequest(sub.symbol(), sub.exchangeSegment());
            byFeed.computeIfAbsent(sub.feedMode(), ignored -> new ArrayList<>()).add(request);
        }
        if (coordinator != null) {
            byFeed.forEach((feedMode, requests) -> coordinator.subscribe(requests, feedMode));
        } else {
            byFeed.forEach((feedMode, requests) -> subscribeBatched(requests, feedMode));
        }
    }

    public void applyPromotion(ScanProfile profile, List<ScanHit> hits) {
        PromotionSpec promotion = profile.promotion();
        if (promotion.topN() <= 0 || hits.isEmpty()) {
            return;
        }
        FeedMode feedMode = promotion.feedMode();
        int maxActive = promotion.maxConcurrentPromotions() > 0
                ? promotion.maxConcurrentPromotions()
                : promotion.topN();
        List<ScanHit> toPromote = hits.stream().limit(promotion.topN()).toList();
        Set<MarketSubscriptionRequest> next = new LinkedHashSet<>();
        for (ScanHit hit : toPromote) {
            next.add(new MarketSubscriptionRequest(hit.symbol(), hit.exchangeSegment()));
        }
        lock.lock();
        try {
            expirePromotions(promotion.promotionTtlMinutes());
            Set<MarketSubscriptionRequest> toSubscribe = new LinkedHashSet<>();
            for (MarketSubscriptionRequest request : next) {
                if (!isStatic(request) && !promotedAt.containsKey(request)) {
                    toSubscribe.add(request);
                }
                promotedAt.put(request, Instant.now());
            }
            trimPromotions(maxActive);
            if (!toSubscribe.isEmpty()) {
                subscribeBatched(List.copyOf(toSubscribe), feedMode);
            }
            log.info("Applied scan promotion profile={} newSubscriptions={} activePromoted={}",
                    profile.id(), toSubscribe.size(), promotedAt.size());
        } finally {
            lock.unlock();
        }
    }

    public Set<MarketSubscriptionRequest> activeSubscriptions() {
        lock.lock();
        try {
            Set<MarketSubscriptionRequest> all = new LinkedHashSet<>();
            for (TradingProperties.SubscriptionProperties sub : staticSubscriptions) {
                all.add(new MarketSubscriptionRequest(sub.symbol(), sub.exchangeSegment()));
            }
            all.addAll(promotedAt.keySet());
            return Set.copyOf(all);
        } finally {
            lock.unlock();
        }
    }

    private void expirePromotions(int ttlMinutes) {
        if (ttlMinutes <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(ttlMinutes * 60L);
        List<MarketSubscriptionRequest> expired = promotedAt.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .filter(r -> !isStatic(r))
                .toList();
        if (!expired.isEmpty()) {
            unsubscribeBatched(expired);
            expired.forEach(promotedAt::remove);
            log.info("Demoted {} expired scan promotions", expired.size());
        }
    }

    private void trimPromotions(int max) {
        if (promotedAt.size() <= max) {
            return;
        }
        List<MarketSubscriptionRequest> toRemove = promotedAt.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(promotedAt.size() - max)
                .map(Map.Entry::getKey)
                .filter(r -> !isStatic(r))
                .toList();
        if (!toRemove.isEmpty()) {
            unsubscribeBatched(toRemove);
            toRemove.forEach(promotedAt::remove);
        }
    }

    private boolean isStatic(MarketSubscriptionRequest request) {
        return staticSubscriptions.stream().anyMatch(sub ->
                sub.symbol().equals(request.symbol())
                        && sub.exchangeSegment() == request.exchangeSegment());
    }

    private void subscribeBatched(List<MarketSubscriptionRequest> requests, FeedMode feedMode) {
        for (int offset = 0; offset < requests.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, requests.size());
            websocket.subscribe(requests.subList(offset, end), feedMode);
        }
    }

    private void unsubscribeBatched(List<MarketSubscriptionRequest> requests) {
        for (int offset = 0; offset < requests.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, requests.size());
            websocket.unsubscribe(requests.subList(offset, end));
        }
    }
}
