package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.OrderQuery;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;

import java.util.List;
import java.util.OptionalLong;

final class UpstoxUnsupportedOrderQuery extends UpstoxUnsupportedPort implements OrderQuery {
    @Override
    public Order getOrder(String orderId) {
        throw unsupported("OrderQuery");
    }

    @Override
    public List<Order> getOrderBook() {
        throw unsupported("OrderQuery");
    }

    @Override
    public List<Trade> getTradeBook() {
        throw unsupported("OrderQuery");
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        throw unsupported("OrderQuery");
    }

    @Override
    public OptionalLong getExecutedPricePaisa(String orderId) {
        throw unsupported("OrderQuery");
    }

    @Override
    public OptionalLong getExchangeTimeMs(String orderId) {
        throw unsupported("OrderQuery");
    }
}
