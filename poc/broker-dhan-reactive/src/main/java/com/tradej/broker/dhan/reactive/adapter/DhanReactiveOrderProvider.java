package com.tradej.broker.dhan.reactive.adapter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.reactive.port.ReactiveOrderProvider;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive order provider for Dhan.
 * 
 * GREEN Phase: Minimal implementation to make tests pass.
 */
public final class DhanReactiveOrderProvider implements ReactiveOrderProvider {
    
    private static final Logger log = LoggerFactory.getLogger(DhanReactiveOrderProvider.class);
    
    private final DhanReactiveHttpClient httpClient;
    
    public DhanReactiveOrderProvider(DhanReactiveHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    @Override
    public Mono<String> placeOrder(OrderRequest request) {
        ObjectNode payload = buildOrderPayload(request);
        
        return httpClient.postJson("/orders", payload)
            .map(response -> response.get("orderId").asText())
            .doOnSuccess(orderId -> log.info("Order placed: {}", orderId))
            .doOnError(ex -> log.error("Failed to place order: {}", ex.getMessage()));
    }
    
    @Override
    public Mono<String> modifyOrder(String orderId, OrderRequest request) {
        ObjectNode payload = buildOrderPayload(request);
        
        return httpClient.putJson("/orders/" + orderId, payload)
            .map(response -> response.get("orderId").asText())
            .doOnSuccess(id -> log.info("Order modified: {}", id));
    }
    
    @Override
    public Mono<Boolean> cancelOrder(String orderId) {
        return httpClient.deleteJson("/orders/" + orderId)
            .map(response -> true)
            .doOnSuccess(success -> log.info("Order cancelled: {}", orderId))
            .doOnError(ex -> log.error("Failed to cancel order {}: {}", orderId, ex.getMessage()));
    }
    
    @Override
    public Mono<OrderStatus> getOrderStatus(String orderId) {
        return httpClient.getJson("/orders/" + orderId)
            .map(response -> {
                String status = response.get("orderStatus").asText();
                return OrderStatus.valueOf(status);
            });
    }
    
    @Override
    public Flux<OrderStatus> getAllOrders() {
        // TODO: Implement
        return Flux.empty();
    }
    
    private ObjectNode buildOrderPayload(OrderRequest request) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("symbol", request.symbol());
        payload.put("exchangeSegment", request.exchangeSegment().name());
        payload.put("side", request.side().name());
        payload.put("quantity", request.quantity());
        payload.put("orderType", request.orderType().name());
        
        if (request.pricePaisa() > 0) {
            payload.put("price", request.pricePaisa() / 100.0);
        }
        
        if (request.triggerPricePaisa() > 0) {
            payload.put("triggerPrice", request.triggerPricePaisa() / 100.0);
        }
        
        payload.put("productType", request.productType().name());
        payload.put("validity", request.validity().name());
        
        return payload;
    }
}
