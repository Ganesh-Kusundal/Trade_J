package com.tradej.broker.icici.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Instrument;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BreezeDomainMapper {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter BREEZE_EXPIRY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'06:00:00.000'Z'");

    public ObjectNode toQuotesPayload(BreezeInstrumentDefinition definition) {
        ObjectNode payload = emptyPayloadInternal();
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
        ObjectNode payload = emptyPayloadInternal();
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
        ObjectNode payload = emptyPayloadInternal();
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
        ObjectNode payload = emptyPayloadInternal();
        payload.put("order_id", orderId);
        payload.put("exchange_code", exchangeCode);
        return payload;
    }

    public ObjectNode toOrderDetailPayload(String orderId, String exchangeCode) {
        ObjectNode payload = emptyPayloadInternal();
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
        ObjectNode payload = emptyPayloadInternal();
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
        ObjectNode payload = emptyPayloadInternal();
        payload.put("stock_code", definition.breezeStockCode());
        // ICICI option chain API requires exchange_code 'nfo' or 'bfo' (lowercase)
        String optionExchangeCode = switch (definition.exchangeSegment()) {
            case NSE_FNO -> "nfo";
            case BSE_FNO -> "bfo";
            default -> definition.exchangeCode() != null ? definition.exchangeCode().toLowerCase() : "nfo";
        };
        payload.put("exchange_code", optionExchangeCode);
        payload.put("expiry_date", BREEZE_EXPIRY.format(expiry.atStartOfDay(INDIA)));
        payload.put("product_type", "options");
        payload.put("right", "");
        payload.put("strike_price", "");
        return payload;
    }

    public Quote toQuote(JsonNode node, Instrument instrument) {
        long ltp = pricePaisa(node, "ltp", "last", "LTP");
        long open = pricePaisa(node, "open", "Open");
        long high = pricePaisa(node, "high", "High");
        long low = pricePaisa(node, "low", "Low");
        long close = pricePaisa(node, "close", "Close", "previous_close");
        long volume = node.path("total_quantity_traded").asLong(
                node.path("ttq").asLong(node.path("volume").asLong(0L)));
        long totalBuy = node.path("total_buy_quantity").asLong(node.path("totalBuyQt").asLong(0L));
        long totalSell = node.path("total_sell_quantity").asLong(node.path("totalSellQt").asLong(0L));
        long oi = node.path("oi").asLong(node.path("open_interest").asLong(0L));
        return new Quote(instrument, ltp, open, high, low, close, volume, totalBuy, totalSell, oi, Instant.now().toEpochMilli());
    }

    /**
     * Builds a {@link MarketDepth} from the Breeze quotes/depth API response.
     * Breeze returns a top-of-book object ({@code bid}/@{@code ask})
     * and optionally a full depth array ({@code bids}/@{@code asks}).
     * Tries both structures for maximum data fidelity.
     *
     * @param node     parsed JSON node from the quotes endpoint
     * @param instrument the instrument for the depth object
     * @return populated MarketDepth; never null
     */
    public MarketDepth toDepth(JsonNode node, com.tradej.core.domain.model.Instrument instrument) {
        long timestamp = node.has("timestamp")
                ? parseInstant(node.get("timestamp"))
                : Instant.now().toEpochMilli();
        int levels = 0;

        // Strategy 1: full depth arrays (preferred — more levels)
        JsonNode fullBids = node.path("bids");
        JsonNode fullAsks = node.path("asks");
        if (fullBids.isArray() && fullAsks.isArray()) {
            levels = Math.max(fullBids.size(), fullAsks.size());
            return new MarketDepth(instrument, toDepthLevels(fullBids), toDepthLevels(fullAsks), levels, timestamp);
        }

        // Strategy 2: top-of-book single object (bid/ask with nested price/qty/orders)
        JsonNode topBid = node.path("bid");
        JsonNode topAsk = node.path("ask");
        if (topBid.isObject() || topAsk.isObject()) {
            return new MarketDepth(
                    instrument,
                    List.of(toDepthLevel(topBid)),
                    List.of(toDepthLevel(topAsk)),
                    1,
                    timestamp
            );
        }

        // Strategy 3: flat top-of-book fields (price/qty directly on bid/ask node)
        if (node.has("bid_price") || node.has("ask_price")) {
            long bidPx  = pricePaisa(node, "bid_price", "bidPrice", "bid");
            long bidQty = parseLongOrDefault(node, "bid_quantity", "bidQty", "bid_quantity", 0L);
            int  bidOrd = (int) parseLongOrDefault(node, "bid_orders", "bidOrders", "bid_orders", 1L);
            long askPx  = pricePaisa(node, "ask_price", "askPrice", "ask");
            long askQty = parseLongOrDefault(node, "ask_quantity", "askQty", "ask_quantity", 0L);
            int  askOrd = (int) parseLongOrDefault(node, "ask_orders", "askOrders", "ask_orders", 1L);
            List<DepthLevel> bids = bidPx > 0 ? List.of(new DepthLevel(bidPx, bidQty, bidOrd)) : List.of();
            List<DepthLevel> asks = askPx > 0 ? List.of(new DepthLevel(askPx, askQty, askOrd)) : List.of();
            return new MarketDepth(instrument, bids, asks, 1, timestamp);
        }

        // No depth data — return empty depth
        return new MarketDepth(instrument, List.of(), List.of(), 0, timestamp);
    }

    private List<DepthLevel> toDepthLevels(JsonNode array) {
        List<DepthLevel> levels = new java.util.ArrayList<>();
        for (JsonNode e : array) {
            if (e.isObject()) {
                levels.add(new DepthLevel(
                        pricePaisa(e, "price", "p"),
                        parseLongOrDefault(e, "quantity", "qty", "q", 0L),
                        (int) parseLongOrDefault(e, "orders", "o", "ord", 1L)
                ));
            }
        }
        return List.copyOf(levels);
    }

    private static DepthLevel toDepthLevel(JsonNode node) {
        if (!node.isObject() || node.isEmpty()) {
            return new DepthLevel(0L, 0L, 0);
        }
        return new DepthLevel(
                pricePaisaFromNode(node, "price", "p"),
                parseLongOrDefault(node, "quantity", "qty", "q", 0L),
                (int) parseLongOrDefault(node, "orders", "o", "ord", 1L)
        );
    }

    private static long parseInstant(JsonNode node) {
        if (node == null || node.isNull()) return Instant.now().toEpochMilli();
        try { return node.isIntegralNumber() ? node.asLong() : Instant.parse(node.asText()).toEpochMilli(); }
        catch (Exception ignored) { return Instant.now().toEpochMilli(); }
    }

    private static long parseLongOrDefault(JsonNode node, String k1, String k2, String k3, long fallback) {
        for (String k : List.of(k1, k2, k3)) {
            if (node.has(k)) {
                try { return node.get(k).asLong(); } catch (Exception ignored) {}
            }
        }
        return fallback;
    }

    private static long pricePaisaFromNode(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) {
                try { return Math.round(node.get(name).asDouble() * 100.0); } catch (Exception ignored) {}
            }
        }
        return 0L;
    }

    public Order toOrder(JsonNode node, OrderRequest originalRequest) {
        return toOrder(node, originalRequest, null);
    }

    public Order toOrder(JsonNode node, OrderRequest originalRequest, Instrument instrument) {
        String symbol = instrument != null ? instrument.canonicalSymbol()
                : node.path("stock_code").asText(originalRequest != null ? originalRequest.symbol() : "");
        return new Order(
                node.path("order_id").asText(""),
                originalRequest != null ? originalRequest.correlationId() : "",
                symbol,
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

    public ObjectNode emptyPayload() {
        return emptyPayloadInternal();
    }

    private static ObjectNode emptyPayloadInternal() {
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
            case "pending" -> OrderStatus.PENDING;
            case "ordered", "open" -> OrderStatus.OPEN;
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
