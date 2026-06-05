package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.rest.BreezeOrderRestClient;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public final class IciciOrderQueryAdapter implements OrderQuery {
    private final BreezeOrderRestClient restClient;
    private final BreezeDomainMapper mapper;
    private final IciciOrderExchangeResolver exchangeResolver;

    public IciciOrderQueryAdapter(BreezeOrderRestClient restClient, BreezeDomainMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.exchangeResolver = new IciciOrderExchangeResolver(restClient, mapper);
    }

    @Override
    public Order getOrder(String orderId) {
        String exchangeCode = exchangeResolver.resolveExchangeCode(orderId);
        ObjectNode payload = mapper.toOrderDetailPayload(orderId, exchangeCode);
        JsonNode response = restClient.getOrderDetail(payload);
        return mapper.toOrder(response, null);
    }

    @Override
    public List<Order> getOrderBook() {
        List<Order> orders = new ArrayList<>();
        for (JsonNode response : exchangeResolver.fetchOrderListsAcrossExchanges()) {
            for (JsonNode node : response) {
                orders.add(mapper.toOrder(node, null));
            }
        }
        return orders;
    }

    @Override
    public List<Trade> getTradeBook() {
        List<Trade> trades = new ArrayList<>();
        for (JsonNode response : exchangeResolver.fetchTradeBooksAcrossExchanges()) {
            for (JsonNode node : response) {
                trades.add(new Trade(
                        node.path("trade_id").asText(""),
                        node.path("order_id").asText(""),
                        node.path("stock_code").asText(""),
                        com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                        "sell".equalsIgnoreCase(node.path("action").asText(""))
                                ? com.tradej.core.domain.value.Side.SELL
                                : com.tradej.core.domain.value.Side.BUY,
                        parseLong(node.path("quantity").asText("0")),
                        Math.round(node.path("price").asDouble(0.0) * 100.0),
                        System.currentTimeMillis()
                ));
            }
        }
        return trades;
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        return getOrder(orderId).status();
    }

    @Override
    public OptionalLong getExecutedPricePaisa(String orderId) {
        Order order = getOrder(orderId);
        return order.pricePaisa() > 0 ? OptionalLong.of(order.pricePaisa()) : OptionalLong.empty();
    }

    @Override
    public OptionalLong getExchangeTimeMs(String orderId) {
        return OptionalLong.of(getOrder(orderId).exchangeTimeMs());
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return (long) Double.parseDouble(value);
    }
}
