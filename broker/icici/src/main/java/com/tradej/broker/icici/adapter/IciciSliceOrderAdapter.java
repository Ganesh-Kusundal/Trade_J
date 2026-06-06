package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.List;

/**
 * ICICI slice order adapter.
 * ICICI Breeze API does not natively support slice orders.
 */
public final class IciciSliceOrderAdapter implements SliceOrderCommand {

    @Override
    public List<Order> placeSliceOrder(SliceOrderRequest request) {
        throw new UnsupportedOperationException("ICICI does not support slice orders");
    }
}
