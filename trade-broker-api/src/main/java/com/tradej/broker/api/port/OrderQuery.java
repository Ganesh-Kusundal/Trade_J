package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;

import java.util.List;
import java.util.OptionalLong;

public interface OrderQuery {
    Order getOrder(String orderId);

    List<Order> getOrderBook();

    List<Trade> getTradeBook();

    OrderStatus getOrderStatus(String orderId);

    OptionalLong getExecutedPricePaisa(String orderId);

    OptionalLong getExchangeTimeMs(String orderId);
}
