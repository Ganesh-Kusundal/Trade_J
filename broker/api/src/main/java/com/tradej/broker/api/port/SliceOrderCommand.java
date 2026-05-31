package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.List;

public interface SliceOrderCommand {
    List<Order> placeSliceOrder(SliceOrderRequest request);
}
