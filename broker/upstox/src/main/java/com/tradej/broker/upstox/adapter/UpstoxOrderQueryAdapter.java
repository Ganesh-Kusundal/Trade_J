package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;

import java.util.List;
import java.util.OptionalLong;

public final class UpstoxOrderQueryAdapter implements OrderQuery {

    private final UpstoxOrderRestClient restClient;
    private final UpstoxDomainMapper mapper;

    public UpstoxOrderQueryAdapter(UpstoxOrderRestClient restClient, UpstoxDomainMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    @Override
    public Order getOrder(String orderId) {
        var response = restClient.getOrderDetails(orderId);
        return mapper.toOrder(response, null);
    }

    @Override
    public List<Order> getOrderBook() {
        var response = restClient.getOrderBook();
        return mapper.toOrderList(response);
    }

    @Override
    public List<Trade> getTradeBook() {
        var response = restClient.getTrades();
        return mapper.toTradeList(response);
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        Order order = getOrder(orderId);
        return order != null ? order.status() : OrderStatus.UNKNOWN;
    }

    @Override
    public OptionalLong getExecutedPricePaisa(String orderId) {
        return getTradeBook().stream()
                .filter(t -> t.orderId().equals(orderId))
                .mapToLong(t -> t.pricePaisa())
                .average()
                .stream()
                .mapToLong(Math::round)
                .findFirst();
    }

    @Override
    public OptionalLong getExchangeTimeMs(String orderId) {
        Order order = getOrder(orderId);
        if (order != null && order.exchangeTimeMs() > 0) {
            return OptionalLong.of(order.exchangeTimeMs());
        }
        return OptionalLong.empty();
    }
}
