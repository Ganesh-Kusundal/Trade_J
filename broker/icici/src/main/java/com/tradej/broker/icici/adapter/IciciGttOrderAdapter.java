package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

/**
 * ICICI GTT order adapter.
 * ICICI Breeze API does not natively support GTT (Good-Till-Triggered) orders.
 */
public final class IciciGttOrderAdapter implements GttOrderProvider {

    @Override
    public Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        throw new UnsupportedOperationException("ICICI does not support GTT orders natively");
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        throw new UnsupportedOperationException("ICICI does not support GTT orders natively");
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        throw new UnsupportedOperationException("ICICI does not support GTT orders natively");
    }

    @Override
    public List<Order> getForeverOrders() {
        return List.of();
    }
}
