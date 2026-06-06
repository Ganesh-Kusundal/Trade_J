package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.mapper.IciciExchangeSegmentMapper;
import com.tradej.broker.icici.rest.BreezeOrderRestClient;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;

import java.time.Instant;
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
        for (String exchangeCode : IciciExchangeSegmentMapper.supportedOrderBookExchangeCodes()) {
            JsonNode response = fetchTradeBookForExchange(exchangeCode);
            if (response == null || !response.isArray() || response.isEmpty()) {
                continue;
            }
            ExchangeSegment segment = IciciExchangeSegmentMapper.fromIciciCode(exchangeCode);
            for (JsonNode node : response) {
                trades.add(new Trade(
                        node.path("trade_id").asText(""),
                        node.path("order_id").asText(""),
                        node.path("stock_code").asText(""),
                        segment,
                        "sell".equalsIgnoreCase(node.path("action").asText(""))
                                ? com.tradej.core.domain.value.Side.SELL
                                : com.tradej.core.domain.value.Side.BUY,
                        parseLong(node.path("quantity").asText("0")),
                        Math.round(node.path("price").asDouble(0.0) * 100.0),
                        exchangeTimestampMs(node)
                ));
            }
        }
        return trades;
    }

    private JsonNode fetchTradeBookForExchange(String exchangeCode) {
        ObjectNode payload = mapper.emptyPayload();
        payload.put("exchange_code", exchangeCode);
        return restClient.getTrades(payload);
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

    private static long exchangeTimestampMs(JsonNode node) {
        // Breeze may return exchange time as epoch millis, epoch seconds, or ISO string.
        for (String field : List.of("exchange_time", "exchangeTime", "trade_time", "tradeTime",
                "exchange_timestamp", "exchangeTimestamp", "trade_timestamp")) {
            if (node.has(field)) {
                try {
                    JsonNode f = node.get(field);
                    if (f.isIntegralNumber()) {
                        long val = f.asLong();
                        // If value < 10^12, treat as epoch seconds → convert to millis
                        return val < 1_000_000_000_000L ? val * 1000L : val;
                    }
                    if (f.isTextual()) {
                        return Instant.parse(f.asText()).toEpochMilli();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return System.currentTimeMillis();
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return (long) Double.parseDouble(value);
    }
}
