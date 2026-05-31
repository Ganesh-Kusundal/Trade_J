package com.tradej.broker.upstox.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Upstox REST API JSON responses to domain models.
 * No SDK dependency — pure JSON node processing.
 */
public final class UpstoxDomainMapper {

    public UpstoxDomainMapper() {
    }

    public Map<String, Object> toPlaceOrderPayload(OrderRequest request, String instrumentKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instrument_key", instrumentKey);
        payload.put("quantity", request.quantity());
        payload.put("product", mapProductType(request.productType()));
        payload.put("validity", mapValidity(request.validity()));
        payload.put("order_type", mapOrderType(request.orderType()));
        payload.put("transaction_type", mapSide(request.side()));
        if (request.pricePaisa() > 0) {
            payload.put("price", request.pricePaisa() / 100.0);
        }
        if (request.triggerPricePaisa() > 0) {
            payload.put("trigger_price", request.triggerPricePaisa() / 100.0);
        }
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            payload.put("tag", request.correlationId());
        }
        // Default: not AMO; caller can override via correlationId prefix if needed
        payload.put("is_amo", false);
        return payload;
    }

    public Map<String, Object> toModifyOrderPayload(ModifyOrderRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", request.orderId());
        if (request.quantity() != null) {
            payload.put("quantity", request.quantity());
        }
        if (request.pricePaisa() != null && request.pricePaisa() > 0) {
            payload.put("price", request.pricePaisa() / 100.0);
        }
        if (request.triggerPricePaisa() != null && request.triggerPricePaisa() > 0) {
            payload.put("trigger_price", request.triggerPricePaisa() / 100.0);
        }
        return payload;
    }

    public Order toOrder(JsonNode response, OrderRequest originalRequest) {
        JsonNode data = response.get("data");
        if (data == null) data = response;
        return new Order(
                data.has("order_id") ? data.get("order_id").asText() : "",
                originalRequest != null ? originalRequest.correlationId() : "",
                data.has("trading_symbol") ? data.get("trading_symbol").asText() : "",
                parseSegment(data),
                parseSide(data),
                parseProductType(data),
                parseOrderType(data),
                parseStatus(data),
                data.has("quantity") ? data.get("quantity").asLong() : 0L,
                data.has("filled_quantity") ? data.get("filled_quantity").asLong() : 0L,
                data.has("price") ? (long) (data.get("price").asDouble() * 100) : 0L,
                data.has("trigger_price") ? (long) (data.get("trigger_price").asDouble() * 100) : 0L,
                data.has("exchange_timestamp") ? parseTimestamp(data.get("exchange_timestamp")) : 0L,
                data.has("status_message") ? data.get("status_message").asText() : ""
        );
    }

    public List<Order> toOrderList(JsonNode response) {
        List<Order> orders = new ArrayList<>();
        JsonNode data = response.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                orders.add(toOrder(node, null));
            }
        } else if (data != null && data.isObject()) {
            orders.add(toOrder(data, null));
        }
        return orders;
    }

    public List<Trade> toTradeList(JsonNode response) {
        List<Trade> trades = new ArrayList<>();
        JsonNode data = response.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                trades.add(new Trade(
                        node.has("trade_id") ? node.get("trade_id").asText() : "",
                        node.has("order_id") ? node.get("order_id").asText() : "",
                        node.has("trading_symbol") ? node.get("trading_symbol").asText() : "",
                        parseSegment(node),
                        parseSide(node),
                        node.has("quantity") ? node.get("quantity").asLong() : 0L,
                        node.has("price") ? (long) (node.get("price").asDouble() * 100) : 0L,
                        node.has("exchange_timestamp") ? parseTimestamp(node.get("exchange_timestamp")) : 0L
                ));
            }
        }
        return trades;
    }

    public boolean isSuccess(JsonNode response) {
        JsonNode status = response.get("status");
        return status != null && "success".equals(status.asText());
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    static ExchangeSegment parseSegment(JsonNode node) {
        if (!node.has("exchange")) return ExchangeSegment.NSE_EQ;
        return switch (node.get("exchange").asText().toUpperCase()) {
            case "NSE" -> ExchangeSegment.NSE_EQ;
            case "BSE" -> ExchangeSegment.BSE_EQ;
            case "NFO" -> ExchangeSegment.NSE_FNO;
            case "BFO" -> ExchangeSegment.BSE_FNO;
            case "MCX" -> ExchangeSegment.MCX_COMM;
            default -> ExchangeSegment.NSE_EQ;
        };
    }

    static Side parseSide(JsonNode node) {
        if (!node.has("transaction_type")) return Side.BUY;
        return "BUY".equalsIgnoreCase(node.get("transaction_type").asText()) ? Side.BUY : Side.SELL;
    }

    static ProductType parseProductType(JsonNode node) {
        if (!node.has("product")) return ProductType.INTRADAY;
        return switch (node.get("product").asText().toUpperCase()) {
            case "CNC" -> ProductType.CNC;
            case "MIS" -> ProductType.INTRADAY;
            case "NRML" -> ProductType.CARRY_FORWARD;
            case "MARGIN" -> ProductType.MARGIN;
            default -> ProductType.INTRADAY;
        };
    }

    static OrderType parseOrderType(JsonNode node) {
        if (!node.has("order_type")) return OrderType.MARKET;
        return switch (node.get("order_type").asText().toUpperCase()) {
            case "MARKET" -> OrderType.MARKET;
            case "LIMIT" -> OrderType.LIMIT;
            case "SL" -> OrderType.STOP_LOSS;
            case "SL-M" -> OrderType.STOP_LOSS_MARKET;
            default -> OrderType.MARKET;
        };
    }

    static OrderStatus parseStatus(JsonNode node) {
        if (!node.has("status")) return OrderStatus.UNKNOWN;
        return switch (node.get("status").asText().toUpperCase()) {
            case "OPEN" -> OrderStatus.OPEN;
            case "PENDING" -> OrderStatus.PENDING;
            case "COMPLETE", "FILLED" -> OrderStatus.TRADED;
            case "PARTIALLY_FILLED" -> OrderStatus.PART_TRADED;
            case "CANCELLED", "CANCELED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    static String mapProductType(ProductType pt) {
        return switch (pt) {
            case INTRADAY -> "MIS";
            case CNC -> "CNC";
            case CARRY_FORWARD -> "NRML";
            case MARGIN -> "MARGIN";
        };
    }

    static String mapValidity(Validity v) {
        return switch (v) {
            case DAY -> "DAY";
            case IOC -> "IOC";
        };
    }

    static String mapOrderType(OrderType ot) {
        return switch (ot) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP_LOSS -> "SL";
            case STOP_LOSS_MARKET -> "SL-M";
        };
    }

    static String mapSide(Side s) {
        return s == Side.BUY ? "BUY" : "SELL";
    }

    static long parseTimestamp(JsonNode node) {
        if (node == null) return System.currentTimeMillis();
        if (node.isNumber()) {
            long val = node.asLong();
            return val > 1_000_000_000_000L ? val : val * 1000L;
        }
        return System.currentTimeMillis();
    }
}
