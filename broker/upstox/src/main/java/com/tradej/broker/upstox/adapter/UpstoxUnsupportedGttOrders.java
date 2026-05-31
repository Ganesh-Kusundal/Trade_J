package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

final class UpstoxUnsupportedGttOrders extends UpstoxUnsupportedPort implements GttOrderProvider {
    @Override
    public Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        throw unsupported("GttOrderProvider");
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        throw unsupported("GttOrderProvider");
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        throw unsupported("GttOrderProvider");
    }

    @Override
    public List<Order> getForeverOrders() {
        throw unsupported("GttOrderProvider");
    }
}
