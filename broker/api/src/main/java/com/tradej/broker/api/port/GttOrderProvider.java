package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

public interface GttOrderProvider {
    Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa);

    Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa);

    boolean cancelForeverOrder(String orderId);

    List<Order> getForeverOrders();
}
