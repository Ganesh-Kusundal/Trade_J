package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;

import java.util.List;
import java.util.OptionalLong;

public final class DhanOrderQueryAdapter extends DhanBaseRestAdapter implements OrderQuery {
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanOrderQueryAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanRetryExecutor resilienceExecutor,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.settings = settings;
        this.restOrderClient = restOrderClient;
    }

    @Override
    public Order getOrder(String orderId) {
        return execute(ApiCategory.ORDER, "get-order", () -> {
            Order order;
            if (settings.isSandbox()) {
                order = restOrderClient.getOrder(orderId);
            } else {
                Object raw = restOrderClient.fetchOrderByIdViaApi(orderId, settings);
                DhanJsonResponse response = DhanJsonMapper.wrap(raw);
                DhanJsonResponse data = response.has("data") ? response.path("data") : response;
                order = DhanJsonMapper.toOrder(data, null, null);
            }
            return resolveOrder(order);
        });
    }

    /**
     * Retrieve order status by Dhan correlation ID.
     * Delegates to {@code GET /orders/external/{correlation-id}} in live mode,
     * falls back to iterating the order book in sandbox mode.
     */
    public Order getOrderByCorrelationId(String correlationId) {
        return execute(ApiCategory.ORDER, "get-order-by-correlation", () -> {
            Order order;
            if (settings.isSandbox()) {
                order = getOrderBook().stream()
                        .filter(o -> correlationId.equals(o.correlationId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No order found with correlation ID " + correlationId));
            } else {
                Object raw = restOrderClient.fetchOrderByCorrelationIdViaApi(correlationId, settings);
                DhanJsonResponse response = DhanJsonMapper.wrap(raw);
                DhanJsonResponse data = response.has("data") ? response.path("data") : response;
                order = DhanJsonMapper.toOrder(data, null, null);
            }
            return resolveOrder(order);
        });
    }

    @Override
    public List<Order> getOrderBook() {
        return execute(ApiCategory.ORDER, "get-order-book",
                () -> restOrderClient.getOrders().stream().map(this::resolveOrder).toList()
        );
    }

    @Override
    public List<Trade> getTradeBook() {
        return execute(ApiCategory.ORDER, "get-trade-book", () -> {
            if (settings.isSandbox()) {
                return restOrderClient.getTrades().stream().map(this::resolveTrade).toList();
            }
            List<Object> rawTrades = restOrderClient.fetchTradesViaApi(settings);
            return rawTrades.stream()
                    .map(raw -> {
                        DhanJsonResponse resp = DhanJsonMapper.wrap(raw);
                        DhanInstrumentDefinition def = resolvePayload(resp.raw());
                        return DhanJsonMapper.toTrade(resp, def.toInstrument());
                    })
                    .map(this::resolveTrade)
                    .toList();
        });
    }

    /**
     * Retrieve trade details for a specific order.
     * Delegates to {@code GET /trades/{order-id}} in live mode,
     * filters from the full trade book in sandbox mode.
     */
    public List<Trade> getTradesForOrder(String orderId) {
        return execute(ApiCategory.ORDER, "get-trades-for-order", () -> {
            if (settings.isSandbox()) {
                return getTradeBook().stream()
                        .filter(t -> orderId.equals(t.orderId()))
                        .toList();
            }
            List<Object> rawTrades = restOrderClient.fetchTradesForOrderViaApi(orderId, settings);
            return rawTrades.stream()
                    .map(raw -> {
                        DhanJsonResponse resp = DhanJsonMapper.wrap(raw);
                        DhanInstrumentDefinition def = resolvePayload(resp.raw());
                        return DhanJsonMapper.toTrade(resp, def.toInstrument());
                    })
                    .map(this::resolveTrade)
                    .toList();
        });
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        return getOrder(orderId).status();
    }

    @Override
    public OptionalLong getExecutedPricePaisa(String orderId) {
        List<Trade> trades = getTradeBook().stream().filter(trade -> orderId.equals(trade.orderId())).toList();
        if (trades.isEmpty()) {
            return OptionalLong.empty();
        }
        long totalValue = 0L;
        long totalQuantity = 0L;
        for (Trade trade : trades) {
            totalValue += trade.pricePaisa() * trade.quantity();
            totalQuantity += trade.quantity();
        }
        return totalQuantity == 0 ? OptionalLong.empty() : OptionalLong.of(totalValue / totalQuantity);
    }

    @Override
    public OptionalLong getExchangeTimeMs(String orderId) {
        return getTradeBook().stream()
                .filter(trade -> orderId.equals(trade.orderId()))
                .mapToLong(Trade::exchangeTimeMs)
                .filter(time -> time > 0L)
                .min();
    }

    private Order resolveOrder(Order order) {
        try {
            DhanInstrumentDefinition definition = resolveDef(order.symbol(), order.exchangeSegment());
            return new Order(
                    order.orderId(),
                    order.correlationId(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    order.side(),
                    order.productType(),
                    order.orderType(),
                    order.status(),
                    order.quantity(),
                    order.filledQuantity(),
                    order.pricePaisa(),
                    order.triggerPricePaisa(),
                    order.exchangeTimeMs(),
                    order.rejectionReason()
            );
        } catch (IllegalArgumentException ex) {
            return order;
        }
    }

    private Trade resolveTrade(Trade trade) {
        try {
            DhanInstrumentDefinition definition = resolveDef(trade.symbol(), trade.exchangeSegment());
            return new Trade(
                    trade.tradeId(),
                    trade.orderId(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    trade.side(),
                    trade.quantity(),
                    trade.pricePaisa(),
                    trade.exchangeTimeMs()
            );
        } catch (IllegalArgumentException ex) {
            return trade;
        }
    }
}
