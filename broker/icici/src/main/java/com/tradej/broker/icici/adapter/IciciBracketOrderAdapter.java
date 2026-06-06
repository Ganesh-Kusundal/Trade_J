package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

/**
 * ICICI bracket order adapter.
 * ICICI Breeze API does not natively support bracket orders.
 */
public final class IciciBracketOrderAdapter implements BracketOrderProvider {

    @Override
    public Order placeSuperOrder(OrderRequest request, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa) {
        throw new UnsupportedOperationException("ICICI does not support bracket orders natively");
    }

    @Override
    public Order modifySuperOrder(String orderId, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        throw new UnsupportedOperationException("ICICI does not support bracket orders natively");
    }

    @Override
    public boolean cancelSuperOrder(String orderId, String legName) {
        throw new UnsupportedOperationException("ICICI does not support bracket orders natively");
    }

    @Override
    public List<Order> getSuperOrders() {
        return List.of();
    }
}
