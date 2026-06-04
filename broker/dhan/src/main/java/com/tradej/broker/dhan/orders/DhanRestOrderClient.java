package com.tradej.broker.dhan.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.SliceOrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.PriceMath;

import java.util.ArrayList;
import java.util.List;

public final class DhanRestOrderClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanConnectionSettings settings;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanRetryExecutor resilienceExecutor;

    public DhanRestOrderClient(
            DhanAuthenticatedHttpClient httpClient,
            DhanConnectionSettings settings,
            DhanApiUrlResolver apiUrlResolver,
            DhanRetryExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    public Order placeOrder(OrderRequest request, DhanInstrumentDefinition definition) {
        ObjectNode payload = baseOrderPayload(request, definition);
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-place-order",
                () -> httpClient.postJson(apiUrlResolver.ordersUrl(), payload)
        );
        return toOrder(response, request, definition);
    }

    public Order modifyOrder(ModifyOrderRequest request, DhanInstrumentDefinition definition) {
        ObjectNode payload = mapper.createObjectNode();
        if (request.quantity() != null) payload.put("quantity", request.quantity());
        if (request.pricePaisa() != null) payload.put("price", PriceMath.fromPaisa(request.pricePaisa()).doubleValue());
        if (request.triggerPricePaisa() != null) payload.put("triggerPrice", PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());
        if (request.orderType() != null) payload.put("orderType", request.orderType().name());
        if (request.validity() != null) payload.put("validity", request.validity().name());
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-modify-order",
                () -> httpClient.putJson(apiUrlResolver.orderUrl(request.orderId()), payload)
        );
        Order modified = toOrder(response, null, definition);
        return modified.orderId().isBlank()
                ? new Order(
                request.orderId(),
                modified.correlationId(),
                modified.symbol(),
                modified.exchangeSegment(),
                modified.side(),
                modified.productType(),
                modified.orderType(),
                modified.status(),
                modified.quantity(),
                modified.filledQuantity(),
                modified.pricePaisa(),
                modified.triggerPricePaisa(),
                modified.exchangeTimeMs(),
                modified.rejectionReason()
        )
                : modified;
    }

    public Order getOrder(String orderId) {
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-get-order",
                () -> httpClient.getJson(apiUrlResolver.orderUrl(orderId))
        );
        return toOrder(response, null, null);
    }

    public List<Order> getOrders() {
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-get-orders",
                () -> httpClient.getJson(apiUrlResolver.ordersUrl())
        );
        return parseOrderList(response);
    }

    public List<Trade> getTrades() {
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-get-trades",
                () -> httpClient.getJson(apiUrlResolver.tradesUrl())
        );
        return parseTradeList(response);
    }

    public boolean cancelOrder(String orderId) {
        resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-cancel-order",
                () -> {
                    httpClient.deleteJson(apiUrlResolver.orderUrl(orderId));
                    return true;
                }
        );
        return true;
    }

    public List<String> cancelAllOpenOrders() {
        return resilienceExecutor.execute(ApiCategory.ORDER, "sandbox-cancel-all-orders", () -> {
            httpClient.deleteJson(apiUrlResolver.ordersUrl());
            return List.<String>of();
        });
    }

    public List<String> cancelAndSquareOffIntradayPositions() {
        return cancelAllOpenOrders();
    }

    public List<Order> placeSliceOrder(SliceOrderRequest request, DhanInstrumentDefinition definition) {
        ObjectNode payload = baseOrderPayload(
                new OrderRequest(
                        request.symbol(),
                        request.exchangeSegment(),
                        request.side(),
                        request.quantity(),
                        request.orderType(),
                        request.pricePaisa(),
                        request.triggerPricePaisa(),
                        request.productType(),
                        request.validity(),
                        request.correlationId()
                ),
                definition
        );
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-place-slice-order",
                () -> httpClient.postJson(apiUrlResolver.sliceOrderUrl(), payload)
        );
        DhanJsonResponse data = response.has("data") ? response.path("data") : response;
        if (data.isArray()) {
            List<Order> out = new ArrayList<>();
            for (DhanJsonResponse item : data.asList()) {
                out.add(toOrder(item, null, definition));
            }
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        }
        return List.of(toOrder(response, null, definition));
    }

    public Order placeSuperOrder(OrderRequest request, DhanInstrumentDefinition definition, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa) {
        ObjectNode payload = baseOrderPayload(request, definition);
        payload.put("boProfitValue", PriceMath.fromPaisa(targetPricePaisa).doubleValue());
        payload.put("boStopLossValue", PriceMath.fromPaisa(stopLossPricePaisa).doubleValue());
        payload.put("trailingJump", PriceMath.fromPaisa(trailingJumpPaisa).doubleValue());
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-place-super-order",
                () -> httpClient.postJson(apiUrlResolver.superOrderUrl(), payload)
        );
        return toOrder(response, request, definition);
    }

    public Order placeForeverOrder(OrderRequest request, DhanInstrumentDefinition definition, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        ObjectNode payload = baseOrderPayload(request, definition);
        payload.put("orderFlag", orderFlag);
        if (quantity2 != null) payload.put("quantity2", quantity2);
        if (price2Paisa != null) payload.put("price2", PriceMath.fromPaisa(price2Paisa).doubleValue());
        if (trigger2Paisa != null) payload.put("triggerPrice2", PriceMath.fromPaisa(trigger2Paisa).doubleValue());
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.ORDER,
                "sandbox-place-forever-order",
                () -> httpClient.postJson(apiUrlResolver.foreverOrdersUrl(), payload)
        );
        return toOrder(response, request, definition);
    }

    private ObjectNode baseOrderPayload(OrderRequest request, DhanInstrumentDefinition definition) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dhanClientId", settings.clientId());
        payload.put("securityId", definition.securityId());
        payload.put("exchangeSegment", definition.exchangeSegment().name());
        payload.put("transactionType", request.side().name());
        payload.put("productType", request.productType().name());
        payload.put("orderType", request.orderType().name());
        payload.put("validity", request.validity().name());
        payload.put("quantity", request.quantity());
        if (request.orderType().name().contains("LIMIT")) {
            payload.put("price", PriceMath.fromPaisa(request.pricePaisa()).doubleValue());
        }
        if (request.orderType().name().contains("STOP_LOSS") && request.triggerPricePaisa() > 0L) {
            payload.put("triggerPrice", PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());
        }
        payload.put("afterMarketOrder", false);
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            payload.put("correlationId", request.correlationId());
        }
        return payload;
    }

    private List<Order> parseOrderList(DhanJsonResponse response) {
        DhanJsonResponse data = response.has("data") ? response.path("data") : response;
        if (!data.isArray()) {
            return List.of();
        }
        List<Order> orders = new ArrayList<>();
        for (DhanJsonResponse item : data.asList()) {
            orders.add(toOrder(item, null, null));
        }
        return List.copyOf(orders);
    }

    private List<Trade> parseTradeList(DhanJsonResponse response) {
        DhanJsonResponse data = response.has("data") ? response.path("data") : response;
        if (!data.isArray()) {
            return List.of();
        }
        List<Trade> trades = new ArrayList<>();
        for (DhanJsonResponse item : data.asList()) {
            trades.add(toTrade(item));
        }
        return List.copyOf(trades);
    }

    private Trade toTrade(DhanJsonResponse data) {
        return new Trade(
                data.string("exchangeTradeId", "tradeId", "id"),
                data.string("orderId"),
                data.string("tradingSymbol", "symbol"),
                DhanSdkMapper.segment(data.string("exchangeSegment")),
                DhanSdkMapper.side(data.string("transactionType")),
                data.longValue("tradedQuantity", "quantity"),
                data.decimalPrice("tradedPrice", "price"),
                data.longValue("exchangeTime", "updateTime", "createTime")
        );
    }

    private Order toOrder(DhanJsonResponse response, OrderRequest request, DhanInstrumentDefinition definition) {
        DhanJsonResponse data = response.has("data") ? response.path("data") : response;
        String orderId = data.string("orderId", "id");
        String correlationId = data.string("correlationId");
        String symbol = definition == null ? data.string("tradingSymbol", "symbol") : definition.canonicalSymbol();
        var segment = definition == null
                ? DhanSdkMapper.segment(data.string("exchangeSegment"))
                : definition.exchangeSegment();
        String sideValue = data.string("transactionType");
        String productTypeValue = data.string("productType");
        String orderTypeValue = data.string("orderType");
        String statusValue = data.string("orderStatus", "status");
        if (request != null) {
            if (sideValue.isBlank()) sideValue = request.side().name();
            if (productTypeValue.isBlank()) productTypeValue = request.productType().name();
            if (orderTypeValue.isBlank()) orderTypeValue = request.orderType().name();
            if (correlationId.isBlank()) correlationId = request.correlationId();
        }
        if (productTypeValue.isBlank()) {
            productTypeValue = "INTRADAY";
        }
        if (orderTypeValue.isBlank()) {
            orderTypeValue = "MARKET";
        }
        if (statusValue.isBlank()) {
            statusValue = "PENDING";
        }
        return new Order(
                orderId,
                correlationId,
                symbol,
                segment,
                DhanSdkMapper.side(sideValue),
                DhanSdkMapper.productType(productTypeValue),
                DhanSdkMapper.orderType(orderTypeValue),
                DhanSdkMapper.orderStatus(statusValue),
                data.longValue("quantity"),
                data.longValue("filledQty", "filledQuantity"),
                data.decimalPrice("price"),
                data.decimalPrice("triggerPrice"),
                0L,
                data.string("remarks", "message")
        );
    }
}
