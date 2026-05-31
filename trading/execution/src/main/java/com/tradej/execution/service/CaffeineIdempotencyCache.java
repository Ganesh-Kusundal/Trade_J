package com.tradej.execution.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.core.domain.model.Order;

import java.time.Duration;
import java.util.Optional;

public final class CaffeineIdempotencyCache implements IdempotencyCachePort {
    private final Cache<String, Order> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @Override
    public Optional<Order> get(String clientOrderId) {
        return Optional.ofNullable(cache.getIfPresent(clientOrderId));
    }

    @Override
    public void put(String clientOrderId, Order order) {
        cache.put(clientOrderId, order);
    }

    @Override
    public void remove(String clientOrderId) {
        cache.invalidate(clientOrderId);
    }
}
