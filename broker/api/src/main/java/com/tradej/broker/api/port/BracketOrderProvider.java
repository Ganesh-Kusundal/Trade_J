package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

public interface BracketOrderProvider {
    Order placeSuperOrder(OrderRequest request, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa);

    Order modifySuperOrder(String orderId, String legName, long quantity, long pricePaisa, long triggerPricePaisa);

    boolean cancelSuperOrder(String orderId, String legName);

    List<Order> getSuperOrders();
}
