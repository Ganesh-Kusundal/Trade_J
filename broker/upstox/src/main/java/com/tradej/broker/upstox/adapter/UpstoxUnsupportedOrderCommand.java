package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

final class UpstoxUnsupportedOrderCommand extends UpstoxUnsupportedPort implements OrderCommand {
    @Override
    public Order placeOrder(OrderRequest request) {
        throw unsupported("OrderCommand");
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        throw unsupported("OrderCommand");
    }

    @Override
    public boolean cancelOrder(String orderId) {
        throw unsupported("OrderCommand");
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        throw unsupported("OrderCommand");
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        throw unsupported("OrderCommand");
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        throw unsupported("OrderCommand");
    }
}
