package com.tradej.cli.standalone;

import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.core.domain.model.Order;

import java.util.Optional;

final class NoOpIdempotencyCache implements IdempotencyCachePort {
    @Override
    public Optional<Order> get(String clientOrderId) {
        return Optional.empty();
    }

    @Override
    public void put(String clientOrderId, Order order) {
        // no-op
    }

    @Override
    public void remove(String clientOrderId) {
        // no-op
    }
}
