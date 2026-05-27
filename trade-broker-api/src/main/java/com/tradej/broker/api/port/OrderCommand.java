package com.tradej.broker.api.port;

import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

public interface OrderCommand {
    Order placeOrder(OrderRequest request);

    Order modifyOrder(ModifyOrderRequest request);

    boolean cancelOrder(String orderId);

    List<String> cancelAllOpenOrders();

    List<String> cancelAndSquareOffIntradayPositions();

    boolean setKillSwitch(boolean enabled);
}
