package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Order;

import java.util.Optional;

public interface IdempotencyCachePort {
    Optional<Order> get(String clientOrderId);

    void put(String clientOrderId, Order order);

    void remove(String clientOrderId);
}
