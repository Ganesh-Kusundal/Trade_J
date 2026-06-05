package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

import java.util.*;

public final class UpstoxOrderCommandAdapter implements OrderCommand {

    private final UpstoxOrderRestClient restClient;
    private final UpstoxDomainMapper mapper;
    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxOrderCommandAdapter(
            UpstoxOrderRestClient restClient,
            UpstoxDomainMapper mapper,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        String instrumentKey = instrumentResolver.requireInstrumentKey(
                new com.tradej.core.domain.model.InstrumentKey(request.symbol(), request.exchangeSegment()));
        Map<String, Object> payload = mapper.toPlaceOrderPayload(request, instrumentKey);
        var response = restClient.placeOrder(payload);
        return mapper.toOrder(response, request);
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        Map<String, Object> payload = mapper.toModifyOrderPayload(request);
        var response = restClient.modifyOrder(payload);
        return mapper.toOrder(response, null);
    }

    @Override
    public boolean cancelOrder(String orderId) {
        var response = restClient.cancelOrder(orderId);
        return mapper.isSuccess(response);
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        var orderBook = restClient.getOrderBook();
        List<String> cancelled = new ArrayList<>();
        var orders = orderBook.get("data");
        if (orders != null && orders.isArray()) {
            for (var order : orders) {
                String status = order.has("status") ? order.get("status").asText() : "";
                if ("OPEN".equals(status) || "PENDING".equals(status)) {
                    String orderId = order.get("order_id").asText();
                    try {
                        restClient.cancelOrder(orderId);
                        cancelled.add(orderId);
                    } catch (Exception ignored) {}
                }
            }
        }
        return cancelled;
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public OrderPreview previewOrder(OrderRequest request) {
        // Upstox doesn't have a dedicated preview endpoint; return estimated preview
        // based on order type, quantity, and current market data if available.
        // This is a best-effort implementation.
        String instrumentKey = instrumentResolver.requireInstrumentKey(
                new com.tradej.core.domain.model.InstrumentKey(request.symbol(), request.exchangeSegment()));
        
        // Estimate notional based on order type
        long estimatedNotionalPaisa = 0;
        if (request.orderType() == com.tradej.core.domain.value.OrderType.MARKET) {
            // For market orders, we'd need LTP - skip for now
            estimatedNotionalPaisa = request.quantity() * 100000L; // placeholder
        } else if (request.orderType() == com.tradej.core.domain.value.OrderType.LIMIT
                || request.orderType() == com.tradej.core.domain.value.OrderType.STOP_LOSS
                || request.orderType() == com.tradej.core.domain.value.OrderType.STOP_LOSS_MARKET) {
            estimatedNotionalPaisa = request.quantity() * request.pricePaisa();
        }
        
        return OrderPreview.valid(
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.quantity(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.productType(),
                estimatedNotionalPaisa,
                0L // estimated margin - would need margin API
        );
    }
}
