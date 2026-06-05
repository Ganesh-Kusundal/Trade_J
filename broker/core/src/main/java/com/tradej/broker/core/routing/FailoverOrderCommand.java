package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Places and manages orders with round-robin primary selection and failover on errors.
 */
public final class FailoverOrderCommand implements OrderCommand {

    private final List<IBrokerConnection> connections;
    private final AtomicInteger primaryIndex = new AtomicInteger();
    private final Runnable onRotate;

    public FailoverOrderCommand(List<IBrokerConnection> connections) {
        this(connections, () -> {});
    }

    public FailoverOrderCommand(List<IBrokerConnection> connections, Runnable onRotate) {
        if (connections == null || connections.isEmpty()) {
            throw new IllegalArgumentException("At least one broker connection is required");
        }
        this.connections = List.copyOf(connections);
        this.onRotate = Objects.requireNonNullElse(onRotate, () -> {});
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        return withFailover(connection -> connection.orders().placeOrder(request));
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        return withFailover(connection -> connection.orders().modifyOrder(request));
    }

    @Override
    public boolean cancelOrder(String orderId) {
        return withFailover(connection -> connection.orders().cancelOrder(orderId));
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        return withFailover(connection -> connection.orders().cancelAllOpenOrders());
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        return withFailover(connection -> connection.orders().cancelAndSquareOffIntradayPositions());
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        boolean result = false;
        for (IBrokerConnection connection : connections) {
            result |= connection.orders().setKillSwitch(enabled);
        }
        return result;
    }

    @Override
    public OrderPreview previewOrder(OrderRequest request) {
        // Preview on the current primary connection
        return connections.get(Math.floorMod(primaryIndex.get(), connections.size())).orders().previewOrder(request);
    }

    private <T> T withFailover(Function<IBrokerConnection, T> action) {
        int start = Math.floorMod(primaryIndex.get(), connections.size());
        RuntimeException last = null;
        for (int i = 0; i < connections.size(); i++) {
            IBrokerConnection connection = connections.get(Math.floorMod(start + i, connections.size()));
            try {
                return action.apply(connection);
            } catch (RuntimeException ex) {
                last = ex;
                primaryIndex.incrementAndGet();
                onRotate.run();
            }
        }
        throw new IllegalStateException("All broker order nodes failed", last);
    }
}