package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

/**
 * ICICI cover order adapter.
 * ICICI Breeze API does not natively support cover orders.
 */
public final class IciciCoverOrderAdapter implements CoverOrderProvider {

    @Override
    public Order placeCoverOrder(OrderRequest request, long slPaisa) {
        throw new UnsupportedOperationException("ICICI does not support cover orders");
    }

    @Override
    public Order exitCoverOrder(String orderId) {
        throw new UnsupportedOperationException("ICICI does not support cover orders");
    }
}
