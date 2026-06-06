package com.tradej.broker.dhan.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.instrument.DhanSegmentMapper;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.SliceOrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
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

  // ---------- REST-based methods for live mode ----------

  public List<Object> fetchTradesViaApi(DhanConnectionSettings settings) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-trades",
        () -> httpClient.getJson(apiUrlResolver.tradesUrl())
    );
    if (!response.has("data") || !response.path("data").isArray()) {
      return List.of();
    }
    List<Object> trades = new ArrayList<>();
    for (DhanJsonResponse item : response.path("data").asList()) {
      trades.add(item.raw());
    }
    return List.copyOf(trades);
  }

  public List<Object> fetchTradesForOrderViaApi(String orderId, DhanConnectionSettings settings) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-trades-for-order",
        () -> httpClient.getJson(apiUrlResolver.tradesUrlForOrder(orderId))
    );
    if (!response.has("data") || !response.path("data").isArray()) {
      return List.of();
    }
    List<Object> trades = new ArrayList<>();
    for (DhanJsonResponse item : response.path("data").asList()) {
      trades.add(item.raw());
    }
    return List.copyOf(trades);
  }

  public Object fetchOrderByIdViaApi(String orderId, DhanConnectionSettings settings) {
    return resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-order-by-id",
        () -> httpClient.getJson(apiUrlResolver.orderUrl(orderId))
    ).raw();
  }

  public Object fetchOrderByCorrelationIdViaApi(String correlationId, DhanConnectionSettings settings) {
    return resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-order-by-correlation",
        () -> httpClient.getJson(apiUrlResolver.orderByCorrelationIdUrl(correlationId))
    ).raw();
  }

  public Object modifySuperOrderViaApi(
      String orderId, long quantity, long pricePaisa, long triggerPricePaisa, DhanConnectionSettings settings
  ) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("dhanClientId", settings.clientId());
    payload.put("orderId", orderId);
    payload.put("quantity", quantity);
    payload.put("price", PriceMath.fromPaisa(pricePaisa).doubleValue());
    payload.put("triggerPrice", PriceMath.fromPaisa(triggerPricePaisa).doubleValue());
    return resilienceExecutor.execute(
        ApiCategory.ORDER,
        "modify-super-order",
        () -> httpClient.putJson(apiUrlResolver.superOrderByIdUrl(orderId), payload)
    ).raw();
  }

  public boolean cancelSuperOrderViaApi(String orderId, String legName, DhanConnectionSettings settings) {
    resilienceExecutor.execute(
        ApiCategory.ORDER,
        "cancel-super-order",
        () -> { httpClient.deleteJson(apiUrlResolver.superOrderLegUrl(orderId, legName)); return true; }
    );
    return true;
  }

  public List<Object> fetchSuperOrdersViaApi(DhanConnectionSettings settings) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-super-orders",
        () -> httpClient.getJson(apiUrlResolver.superOrdersListUrl())
    );
    if (!response.has("data") || !response.path("data").isArray()) {
      return List.of();
    }
    List<Object> orders = new ArrayList<>();
    for (DhanJsonResponse item : response.path("data").asList()) {
      orders.add(item.raw());
    }
    return List.copyOf(orders);
  }

  public String getKillSwitchStatusViaApi(DhanConnectionSettings settings) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-kill-switch-status",
        () -> httpClient.getJson(apiUrlResolver.killSwitchUrl())
    );
    return response.string("killSwitchStatus", "status");
  }

  public Object placeOrderViaApi(OrderRequest request, DhanInstrumentDefinition definition, DhanConnectionSettings settings) {
    ObjectNode payload = baseOrderPayload(request, definition);
    return resilienceExecutor.execute(
        ApiCategory.ORDER,
        "place-order",
        () -> httpClient.postJson(apiUrlResolver.ordersUrl(), payload)
    ).raw();
  }

  public Object modifyOrderViaApi(ModifyOrderRequest request, DhanInstrumentDefinition definition, DhanConnectionSettings settings) {
    ObjectNode payload = mapper.createObjectNode();
    if (request.quantity() != null) payload.put("quantity", request.quantity());
    if (request.pricePaisa() != null) payload.put("price", PriceMath.fromPaisa(request.pricePaisa()).doubleValue());
    if (request.triggerPricePaisa() != null) payload.put("triggerPrice", PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());
    if (request.orderType() != null) payload.put("orderType", request.orderType().name());
    if (request.validity() != null) payload.put("validity", request.validity().name());
    return resilienceExecutor.execute(
        ApiCategory.ORDER,
        "modify-order",
        () -> httpClient.putJson(apiUrlResolver.orderUrl(request.orderId()), payload)
    ).raw();
  }

  public boolean cancelOrderViaApi(String orderId, DhanConnectionSettings settings) {
    resilienceExecutor.execute(
        ApiCategory.ORDER,
        "cancel-order",
        () -> { httpClient.deleteJson(apiUrlResolver.orderUrl(orderId)); return true; }
    );
    return true;
  }

  public boolean setKillSwitchViaApi(boolean enabled, DhanConnectionSettings settings) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("killSwitch", enabled ? "ACTIVATE" : "DEACTIVATE");
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "kill-switch",
        () -> httpClient.putJson(apiUrlResolver.ordersUrl(), payload)
    );
    return response.has("data");
  }

  public List<Object> fetchOrdersViaApi(DhanConnectionSettings settings) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-orders",
        () -> httpClient.getJson(apiUrlResolver.ordersUrl())
    );
    if (!response.has("data") || !response.path("data").isArray()) {
      return List.of();
    }
    List<Object> orders = new ArrayList<>();
    for (DhanJsonResponse item : response.path("data").asList()) {
      orders.add(item.raw());
    }
    return List.copyOf(orders);
  }

  public List<Position> fetchPositionsViaApi(DhanConnectionSettings settings) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.NON_TRADING,
        "get-positions",
        () -> httpClient.getJson(apiUrlResolver.positionsUrl())
    );
    if (!response.has("data") || !response.path("data").isArray()) {
      return List.of();
    }
    List<Position> positions = new ArrayList<>();
    for (DhanJsonResponse item : response.path("data").asList()) {
      DhanInstrumentDefinition definition = resolvePayload(item, settings);
      positions.add(DhanJsonMapper.toPosition(item, definition.toInstrument()));
    }
    return List.copyOf(positions);
  }

  private static DhanInstrumentDefinition resolvePayload(DhanJsonResponse item, DhanConnectionSettings settings) {
    String exchange = item.string("exchangeSegment").trim();
    String securityId = item.string("securityId").trim();
    if (exchange.isBlank() || securityId.isBlank()) {
      throw new IllegalArgumentException("Dhan position response missing exchangeSegment or securityId");
    }
    String symbol = item.string("tradingSymbol");
    String canonicalSymbol = item.string("tradingSymbol");
    Exchange exchangeEnum = exchange.contains("NSE") ? Exchange.NSE : Exchange.BSE;
    ExchangeSegment segment = ExchangeSegment.valueOf(exchange);
    String instrumentType = item.string("instrumentType");
    String underlying = item.string("underlying");
    return new DhanInstrumentDefinition(
        symbol,
        canonicalSymbol,
        exchangeEnum,
        segment,
        securityId,
        instrumentType,
        underlying,
        null,
        null,
        null,
        1L,
        1L,
        null
    );
  }

  // ---------- Sandbox REST methods (existing) ----------

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

  public boolean setKillSwitch(boolean enabled) {
    if (!settings.killSwitchTestEnabled()) {
      throw new UnsupportedOperationException("Kill switch not supported in sandbox environment. Enable killSwitchTestEnabled in settings for testing.");
    }
    return true;
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

  public Order modifyForeverOrder(
      String orderId,
      String orderFlag,
      String legName,
      long quantity,
      long pricePaisa,
      long triggerPricePaisa
  ) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("dhanClientId", settings.clientId());
    payload.put("orderId", orderId);
    payload.put("orderFlag", orderFlag);
    payload.put("orderType", "LIMIT");
    payload.put("legName", legName);
    payload.put("quantity", quantity);
    payload.put("price", PriceMath.fromPaisa(pricePaisa).doubleValue());
    payload.put("triggerPrice", PriceMath.fromPaisa(triggerPricePaisa).doubleValue());
    payload.put("validity", "DAY");
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "modify-forever-order",
        () -> httpClient.putJson(apiUrlResolver.foreverOrderUrl(orderId), payload)
    );
    return toOrder(response, null, null);
  }

  public boolean cancelForeverOrder(String orderId) {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "cancel-forever-order",
        () -> httpClient.deleteJson(apiUrlResolver.foreverOrderUrl(orderId))
    );
    String status = response.string("orderStatus", "status");
    return status.isBlank() || "CANCELLED".equalsIgnoreCase(status);
  }

  public List<Order> getForeverOrders() {
    DhanJsonResponse response = resilienceExecutor.execute(
        ApiCategory.ORDER,
        "get-forever-orders",
        () -> httpClient.getJson(apiUrlResolver.foreverOrdersAllUrl())
    );
    return parseForeverOrderList(response);
  }

  // ---------- Helpers ----------

  private ObjectNode baseOrderPayload(OrderRequest request, DhanInstrumentDefinition definition) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("dhanClientId", settings.clientId());
    payload.put("securityId", definition.securityId());
    payload.put("exchangeSegment", DhanSegmentMapper.toWireValue(definition.exchangeSegment()));
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

  private List<Order> parseForeverOrderList(DhanJsonResponse response) {
    DhanJsonResponse data = response.has("data") ? response.path("data") : response;
    if (!data.isArray()) {
      return List.of();
    }
    List<Order> orders = new ArrayList<>();
    for (DhanJsonResponse item : data.asList()) {
      orders.add(DhanJsonMapper.toForeverOrder(item));
    }
    return List.copyOf(orders);
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
    return DhanJsonMapper.toTrade(data, null);
  }

  private Order toOrder(DhanJsonResponse response, OrderRequest request, DhanInstrumentDefinition definition) {
    return DhanJsonMapper.toOrder(response, request, definition);
  }
}