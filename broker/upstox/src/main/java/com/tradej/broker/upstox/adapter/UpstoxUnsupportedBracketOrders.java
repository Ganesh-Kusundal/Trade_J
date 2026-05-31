package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

final class UpstoxUnsupportedBracketOrders extends UpstoxUnsupportedPort implements BracketOrderProvider {
    @Override
    public Order placeSuperOrder(OrderRequest request, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa) {
        throw unsupported("BracketOrderProvider");
    }

    @Override
    public Order modifySuperOrder(String orderId, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        throw unsupported("BracketOrderProvider");
    }

    @Override
    public boolean cancelSuperOrder(String orderId, String legName) {
        throw unsupported("BracketOrderProvider");
    }

    @Override
    public List<Order> getSuperOrders() {
        throw unsupported("BracketOrderProvider");
    }
}
