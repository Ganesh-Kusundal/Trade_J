package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.List;

final class UpstoxUnsupportedSliceOrders extends UpstoxUnsupportedPort implements SliceOrderCommand {
    @Override
    public List<Order> placeSliceOrder(SliceOrderRequest request) {
        throw unsupported("SliceOrderCommand");
    }
}
