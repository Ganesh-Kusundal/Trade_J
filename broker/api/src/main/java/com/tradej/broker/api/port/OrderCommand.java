package com.tradej.broker.api.port;

import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;
import java.util.Optional;

public interface OrderCommand {
    Order placeOrder(OrderRequest request);

    Order modifyOrder(ModifyOrderRequest request);

    boolean cancelOrder(String orderId);

    List<String> cancelAllOpenOrders();

    List<String> cancelAndSquareOffIntradayPositions();

    boolean setKillSwitch(boolean enabled);

    default Optional<String> getKillSwitchStatus() {
        return Optional.empty();
    }

    /**
     * Preview an order without placing it. Returns estimated notional, margin,
     * and validation results.
     *
     * @param request the order request to preview
     * @return preview with estimated costs and validation issues
     */
    OrderPreview previewOrder(OrderRequest request);
}