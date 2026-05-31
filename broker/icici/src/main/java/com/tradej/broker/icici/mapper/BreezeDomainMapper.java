package com.tradej.broker.icici.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BreezeDomainMapper {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter BREEZE_EXPIRY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'06:00:00.000'Z'");

    public ObjectNode toQuotesPayload(BreezeInstrumentDefinition definition) {
        ObjectNode payload = emptyPayload();
        payload.put("stock_code", definition.breezeStockCode());
        payload.put("exchange_code", definition.exchangeCode());
        if ("NFO".equalsIgnoreCase(definition.exchangeCode())) {
            payload.put("product_type", "futures");
            payload.put("right", "others");
            payload.put("strike_price", "0");
            payload.put("expiry_date", BREEZE_EXPIRY.format(LocalDate.now(INDIA).atStartOfDay(INDIA)));
        }
        return payload;
    }

    public ObjectNode toPlaceOrderPayload(OrderRequest request, BreezeInstrumentDefinition definition) {
        if (request.orderType() == OrderType.MARKET) {
            throw new UnsupportedOperationException("ICICI Breeze API does not permit market orders");
        }
        ObjectNode payload = emptyPayload();
        payload.put("stock_code", definition.breezeStockCode());
        payload.put("exchange_code", definition.exchangeCode());
        payload.put("product", mapProduct(request.productType(), definition.exchangeCode()));
        payload.put("action", mapSide(request.side()));
        payload.put("order_type", mapOrderType(request.orderType()));
        payload.put("quantity", String.valueOf(request.quantity()));
        payload.put("price", formatPrice(request.pricePaisa()));
        payload.put("validity", mapValidity(request.validity()));
        payload.put("disclosed_quantity", "0");
        if ("NFO".equalsIgnoreCase(definition.exchangeCode())) {
            payload.put("expiry_date", BREEZE_EXPIRY.format(LocalDate.now(INDIA).atStartOfDay(INDIA)));
            payload.put("right", "others");
            payload.put("strike_price", "0");
        }
        if (request.triggerPricePaisa() > 0) {
            payload.put("stoploss", formatPrice(request.triggerPricePaisa()));
        }
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            payload.put("user_remark", sanitizeRemark(request.correlationId()));
        }
        return payload;
    }

    public ObjectNode toModifyOrderPayload(ModifyOrderRequest request, String exchangeCode) {
        ObjectNode payload = emptyPayload();
        payload.put("order_id", request.orderId());
        payload.put("exchange_code", exchangeCode);
        if (request.quantity() != null) {
            payload.put("quantity", String.valueOf(request.quantity()));
        }
        if (request.pricePaisa() != null) {
            payload.put("price", formatPrice(request.pricePaisa()));
        }
        if (request.triggerPricePaisa() != null && request.triggerPricePaisa() > 0) {
            payload.put("stoploss", formatPrice(request.triggerPricePaisa()));
        }
        if (request.orderType() != null) {
            payload.put("order_type", mapOrderType(request.orderType()));
        }
        if (request.validity() != null) {
            payload.put("validity", mapValidity(request.validity()));
        }
        return payload;
    }

    public ObjectNode toCancelOrderPayload(String orderId, String exchangeCode) {
        ObjectNode payload = emptyPayload();
        payload.put("order_id", orderId);
        payload.put("exchange_code", exchangeCode);
        return payload;
    }

    public ObjectNode toOrderDetailPayload(String orderId, String exchangeCode) {
        ObjectNode payload = emptyPayload();
        payload.put("order_id", orderId);
        payload.put("exchange_code", exchangeCode);
        return payload;
    }

    public ObjectNode toHistoricalPayload(
            BreezeInstrumentDefinition definition,
            String interval,
            String fromDate,
            String toDate
    ) {
        ObjectNode payload = emptyPayload();
        payload.put("interval", interval);
        payload.put("from_date", fromDate);
        payload.put("to_date", toDate);
        payload.put("stock_code", definition.breezeStockCode());
        payload.put("exchange_code", definition.exchangeCode());
        payload.put("product_type", mapProduct(ProductType.CNC, definition.exchangeCode()));
        return payload;
    }

    /**
     * Query params for {@code get_historical_data_v2} ({@code exch_code}, {@code 1second} intervals).
     */
    public Map<String, String> toHistoricalV2QueryParams(
            BreezeInstrumentDefinition definition,
            String interval,
            String fromDate,
            String toDate
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("interval", interval);
        params.put("from_date", fromDate);
        params.put("to_date", toDate);
        params.put("stock_code", definition.breezeStockCode());
        params.put("exch_code", definition.exchangeCode().toLowerCase(Locale.ROOT));
        params.put("product_type", mapProduct(ProductType.CNC, definition.exchangeCode()));
        return params;
    }

    public ObjectNode toOptionChainPayload(BreezeInstrumentDefinition definition, LocalDate expiry) {
        ObjectNode payload = emptyPayload();
        payload.put("stock_code", definition.breezeStockCode());
        payload.put("exchange_code", definition.exchangeCode());
        payload.put("expiry_date", BREEZE_EXPIRY.format(expiry.atStartOfDay(INDIA)));
        payload.put("product_type", "options");
        payload.put("right", "");
        payload.put("strike_price", "");
        return payload;
    }

    public Quote toQuote(JsonNode node, com.tradej.core.domain.model.Instrument instrument) {
        long ltp = pricePaisa(node, "ltp", "last", "LTP");
        long open = pricePaisa(node, "open", "Open");
        long high = pricePaisa(node, "high", "High");
        long low = pricePaisa(node, "low", "Low");
        long close = pricePaisa(node, "close", "Close", "previous_close");
        long volume = node.path("total_quantity_traded").asLong(
                node.path("ttq").asLong(node.path("volume").asLong(0L)));
        long totalBuy = node.path("total_buy_quantity").asLong(node.path("totalBuyQt").asLong(0L));
        long totalSell = node.path("total_sell_quantity").asLong(node.path("totalSellQt").asLong(0L));
        return new Quote(
                instrument,
                ltp,
                open,
                high,
                low,
                close,
                volume,
                totalBuy,
                totalSell,
                Instant.now().toEpochMilli()
        );
    }

    public Order toOrder(JsonNode node, OrderRequest originalRequest) {
        return new Order(
                node.path("order_id").asText(""),
                originalRequest != null ? originalRequest.correlationId() : "",
                node.path("stock_code").asText(originalRequest != null ? originalRequest.symbol() : ""),
                originalRequest != null ? originalRequest.exchangeSegment() : null,
                parseSide(node.path("action").asText("")),
                originalRequest != null ? originalRequest.productType() : ProductType.CNC,
                parseOrderType(node.path("order_type").asText("")),
                parseStatus(node.path("status").asText("")),
                parseLong(node.path("quantity").asText("0")),
                filledQuantity(node),
                pricePaisa(node, "price"),
                pricePaisa(node, "stoploss"),
                Instant.now().toEpochMilli(),
                node.path("message").asText("")
        );
    }

    private static long filledQuantity(JsonNode node) {
        long quantity = parseLong(node.path("quantity").asText("0"));
        long pending = parseLong(node.path("pending_quantity").asText("0"));
        return Math.max(0L, quantity - pending);
    }

    private static ObjectNode emptyPayload() {
        return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    }

    private static String mapProduct(ProductType productType, String exchangeCode) {
        if ("NFO".equalsIgnoreCase(exchangeCode)) {
            return "futures";
        }
        return switch (productType) {
            case INTRADAY -> "cash";
            case CNC -> "cash";
            default -> "cash";
        };
    }

    private static String mapSide(Side side) {
        return side == Side.SELL ? "sell" : "buy";
    }

    private static Side parseSide(String action) {
        return "sell".equalsIgnoreCase(action) ? Side.SELL : Side.BUY;
    }

    private static String mapOrderType(OrderType orderType) {
        return orderType == OrderType.STOP_LOSS || orderType == OrderType.STOP_LOSS_MARKET ? "stoploss" : "limit";
    }

    private static OrderType parseOrderType(String orderType) {
        return "stoploss".equalsIgnoreCase(orderType) ? OrderType.STOP_LOSS : OrderType.LIMIT;
    }

    private static String mapValidity(Validity validity) {
        return validity == Validity.IOC ? "ioc" : "day";
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null) {
            return OrderStatus.UNKNOWN;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "executed", "complete" -> OrderStatus.TRADED;
            case "cancelled", "canceled" -> OrderStatus.CANCELLED;
            case "rejected" -> OrderStatus.REJECTED;
            case "partially executed" -> OrderStatus.PART_TRADED;
            case "ordered", "open", "pending" -> OrderStatus.OPEN;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private static String formatPrice(long pricePaisa) {
        return String.format(Locale.ROOT, "%.2f", pricePaisa / 100.0);
    }

    private static long pricePaisa(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isNumber()) {
                    return Math.round(value.asDouble() * 100.0);
                }
                String text = value.asText("");
                if (!text.isBlank()) {
                    return Math.round(Double.parseDouble(text) * 100.0);
                }
            }
        }
        return 0L;
    }

    private static long parseLong(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        return parseLong(node.asText("0"));
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return (long) Double.parseDouble(value);
    }

    private static String sanitizeRemark(String remark) {
        return remark.replaceAll("[^A-Za-z0-9]", "");
    }
}
