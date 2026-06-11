package com.tradej.execution.service;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.Side;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class ExecutionOrderPlacer {
    private final OrderManagementService orderManagementService;
    private final RuntimeModeHolder runtimeModeHolder;
    private final long orderPlacementTimeoutMs;

    ExecutionOrderPlacer(OrderManagementService orderManagementService, RuntimeModeHolder runtimeModeHolder, long orderPlacementTimeoutMs) {
        this.orderManagementService = orderManagementService;
        this.runtimeModeHolder = runtimeModeHolder;
        this.orderPlacementTimeoutMs = orderPlacementTimeoutMs;
    }

    void publishSimulatedFillIfNeeded(Order order, SignalPendingExecution pendingExecution, Consumer<com.tradej.core.domain.event.DomainEvent> downstream) {
        if (!runtimeModeHolder.mode().usesSimulatedExecution() || order.status() != OrderStatus.TRADED) {
            return;
        }
        orderManagementService.lastSimulatedMatch().ifPresent(match -> {
            if (!match.fills().isEmpty()) {
                downstream.accept(new OrderFilled(
                        EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                        order,
                        match.fills()
                ));
            }
        });
    }

    Order placeWithTimeout(OrderRequest request) throws Exception {
        CompletableFuture<Order> placement = CompletableFuture.supplyAsync(
                () -> orderManagementService.placeOrder(request));
        try {
            return placement.get(orderPlacementTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            placement.cancel(true);
            throw new RuntimeException("Order placement timed out after " + orderPlacementTimeoutMs + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Order placement failed", cause);
        }
    }
}
