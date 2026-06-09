package com.tradej.broker.dhan.reactive.port;

import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive order provider interface.
 */
public interface ReactiveOrderProvider {
    
    /**
     * Place a new order.
     */
    Mono<String> placeOrder(OrderRequest request);
    
    /**
     * Modify an existing order.
     */
    Mono<String> modifyOrder(String orderId, OrderRequest request);
    
    /**
     * Cancel an order.
     */
    Mono<Boolean> cancelOrder(String orderId);
    
    /**
     * Get order status.
     */
    Mono<OrderStatus> getOrderStatus(String orderId);
    
    /**
     * Get all orders.
     */
    Flux<OrderStatus> getAllOrders();
}
