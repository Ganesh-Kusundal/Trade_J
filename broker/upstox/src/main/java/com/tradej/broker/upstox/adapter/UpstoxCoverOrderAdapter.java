package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

/**
 * Upstox cover order adapter.
 * Upstox does not natively support cover orders.
 */
public final class UpstoxCoverOrderAdapter implements CoverOrderProvider {

    @Override
    public Order placeCoverOrder(OrderRequest request, long slPaisa) {
        throw new UnsupportedOperationException("Upstox does not support cover orders");
    }

    @Override
    public Order exitCoverOrder(String orderId) {
        throw new UnsupportedOperationException("Upstox does not support cover orders");
    }
}
