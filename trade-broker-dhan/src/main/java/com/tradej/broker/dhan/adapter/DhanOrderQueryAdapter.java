package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;
import java.util.List;
import java.util.OptionalLong;

public final class DhanOrderQueryAdapter extends DhanBaseRestAdapter implements OrderQuery {

    public DhanOrderQueryAdapter(DhanClientHolder clientHolder, DhanInstrumentResolver instrumentResolver, DhanResilienceExecutor resilienceExecutor) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
    }

    @Override
    public Order getOrder(String orderId) {
        return execute(ApiCategory.ORDER, "get-order", () -> {
            DhanSdkResponse<?> order = new DhanSdkResponse<>(clientHolder.client().getOrderById(orderId));
            DhanInstrumentDefinition definition = resolvePayload(order);
            return DhanSdkMapper.toOrder(order, definition.toInstrument());
        });
    }

    @Override
    public List<Order> getOrderBook() {
        return execute(ApiCategory.ORDER, "get-order-book",
                () -> clientHolder.client().getOrders().stream().map(order -> {
                    DhanSdkResponse<?> response = new DhanSdkResponse<>(order);
                    DhanInstrumentDefinition definition = resolvePayload(response);
                    return DhanSdkMapper.toOrder(response, definition.toInstrument());
                }).toList());
    }

    @Override
    public List<Trade> getTradeBook() {
        return execute(ApiCategory.ORDER, "get-trade-book",
                () -> clientHolder.client().getTrades().stream().map(trade -> {
                    DhanSdkResponse<?> response = new DhanSdkResponse<>(trade);
                    DhanInstrumentDefinition definition = resolvePayload(response);
                    return DhanSdkMapper.toTrade(response, definition.toInstrument());
                }).toList());
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
        long totalValue = 0L, totalQuantity = 0L;
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
                .min();
    }
}
