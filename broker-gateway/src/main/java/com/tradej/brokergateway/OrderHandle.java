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
public final class OrderHandle {

    private final BrokerCallSupport support;

    OrderHandle(BrokerSource source, IBrokerConnection connection) {
        this.support = new BrokerCallSupport(source, connection);
    }

    // ── Order Query ─────────────────────────────────────────────

    public GatewayResult<List<Order>> orders() {
        return support.timed(() -> support.connection().orderQuery().getOrderBook());
    }

    public GatewayResult<Order> order(String orderId) {
        return support.timed(() -> support.connection().orderQuery().getOrder(orderId));
    }

    public GatewayResult<List<Trade>> trades() {
        return support.timed(() -> support.connection().orderQuery().getTradeBook());
    }

    // ── Order Command ───────────────────────────────────────────

    public GatewayResult<Order> placeOrder(OrderRequest request) {
        return support.timed(() -> support.connection().orders().placeOrder(request));
    }

    public GatewayResult<Order> modifyOrder(ModifyOrderRequest request) {
        return support.timed(() -> support.connection().orders().modifyOrder(request));
    }

    public GatewayResult<Boolean> cancelOrder(String orderId) {
        return support.timed(() -> support.connection().orders().cancelOrder(orderId));
    }

    public GatewayResult<List<String>> cancelAllOpenOrders() {
        return support.timed(() -> support.connection().orders().cancelAllOpenOrders());
    }

    public GatewayResult<List<String>> cancelAndSquareOff() {
        return support.timed(() -> support.connection().orders().cancelAndSquareOffIntradayPositions());
    }

    public GatewayResult<Boolean> killSwitch(boolean enabled) {
        return support.timed(() -> support.connection().orders().setKillSwitch(enabled));
    }

    public GatewayResult<OrderPreview> previewOrder(OrderRequest request) {
        return support.timed(() -> support.connection().orders().previewOrder(request));
    }
}
