package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;

import java.util.List;

/**
 * Handle for order operations (place, modify, cancel, query).
 * Wraps {@link com.tradej.broker.api.port.OrderCommand} and
 * {@link com.tradej.broker.api.port.OrderQuery} with timing and result metadata.
 */
public final class OrderHandle extends BaseBrokerHandle {

    OrderHandle(BrokerSource source, IBrokerConnection connection) {
        super(source, connection);
    }

    // ── Order Query ─────────────────────────────────────────────

    public GatewayResult<List<Order>> orders() {
        return timed(() -> connection.orderQuery().getOrderBook());
    }

    public GatewayResult<Order> order(String orderId) {
        return timed(() -> connection.orderQuery().getOrder(orderId));
    }

    public GatewayResult<List<Trade>> trades() {
        return timed(() -> connection.orderQuery().getTradeBook());
    }

    // ── Order Command ───────────────────────────────────────────

    public GatewayResult<Order> placeOrder(OrderRequest request) {
        return timed(() -> connection.orders().placeOrder(request));
    }

    public GatewayResult<Order> modifyOrder(ModifyOrderRequest request) {
        return timed(() -> connection.orders().modifyOrder(request));
    }

    public GatewayResult<Boolean> cancelOrder(String orderId) {
        return timed(() -> connection.orders().cancelOrder(orderId));
    }

    public GatewayResult<List<String>> cancelAllOpenOrders() {
        return timed(() -> connection.orders().cancelAllOpenOrders());
    }

    public GatewayResult<List<String>> cancelAndSquareOff() {
        return timed(() -> connection.orders().cancelAndSquareOffIntradayPositions());
    }

    public GatewayResult<Boolean> killSwitch(boolean enabled) {
        return timed(() -> connection.orders().setKillSwitch(enabled));
    }

    public GatewayResult<OrderPreview> previewOrder(OrderRequest request) {
        return timed(() -> connection.orders().previewOrder(request));
    }
}
