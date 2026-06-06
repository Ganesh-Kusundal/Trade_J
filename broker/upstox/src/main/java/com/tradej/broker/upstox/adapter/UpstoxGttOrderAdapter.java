package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.upstox.domain.UpstoxGttOrder;
import com.tradej.broker.upstox.domain.UpstoxGttRule;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxGttRestClient;
import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.PriceMath;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that implements {@link ConditionalAlertProvider} and {@link GttOrderProvider}
 * via the Upstox GTT (v3) API.
 * <p>
 * Also exposes rich GTT-specific methods for full API access.
 */
public final class UpstoxGttOrderAdapter implements ConditionalAlertProvider, GttOrderProvider {

    private final UpstoxGttRestClient restClient;
    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxGttOrderAdapter(
            UpstoxGttRestClient restClient,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this.restClient = restClient;
        this.instrumentResolver = instrumentResolver;
    }

    // ─── ConditionalAlertProvider implementation ──────────────────────────

    @Override
    public String placeAlert(ConditionalAlertRequest request) {
        String instrumentToken = instrumentResolver.requireInstrumentKey(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        String transactionType = request.side() == com.tradej.core.domain.value.Side.BUY ? "BUY" : "SELL";
        String product = mapProduct(request.productType());
        double triggerPrice = PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue();
        String triggerType = request.comparisonType() != null
                ? switch (request.comparisonType().toUpperCase()) {
                    case "ABOVE" -> "ABOVE";
                    case "BELOW" -> "BELOW";
                    default -> "IMMEDIATE";
                  }
                : "IMMEDIATE";
        UpstoxGttRule entryRule = new UpstoxGttRule("ENTRY", triggerType, triggerPrice, null, null);
        JsonNode response = restClient.placeGttOrder(
                "SINGLE", request.quantity(), product,
                instrumentToken, transactionType, List.of(entryRule));
        return extractGttOrderId(response);
    }

    @Override
    public ConditionalAlert getAlert(String alertId) {
        JsonNode response = restClient.getGttOrder(alertId);
        JsonNode data = response.get("data");
        if (data != null && data.isArray() && data.size() > 0) {
            JsonNode order = data.get(0);
            String status = extractOverallStatus(order);
            String message = extractStatusMessage(order);
            return new ConditionalAlert(alertId, status, message);
        }
        return new ConditionalAlert(alertId, "UNKNOWN", "Order not found");
    }

    @Override
    public List<ConditionalAlert> listAlerts() {
        JsonNode response = restClient.getGttOrder(null);
        List<ConditionalAlert> alerts = new ArrayList<>();
        JsonNode data = response.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode order : data) {
                String id = order.has("gtt_order_id") ? order.get("gtt_order_id").asText() : "";
                if (id.isBlank()) continue;
                String status = extractOverallStatus(order);
                String message = extractStatusMessage(order);
                alerts.add(new ConditionalAlert(id, status, message));
            }
        }
        return List.copyOf(alerts);
    }

    @Override
    public boolean deleteAlert(String alertId) {
        restClient.cancelGttOrder(alertId);
        return true;
    }

    // ─── Rich GTT-specific API ────────────────────────────────────────────

    /**
     * Places a GTT order with full control over rules and parameters.
     *
     * @param type            SINGLE or MULTIPLE
     * @param quantity        order quantity
     * @param product         I, D, or MTF
     * @param instrumentKey   resolved Upstox instrument key
     * @param transactionType BUY or SELL
     * @param rules           list of trigger rules
     * @return the GTT order ID
     */
    public String placeGttOrder(
            String type, long quantity, String product,
            String instrumentKey, String transactionType,
            List<UpstoxGttRule> rules) {
        JsonNode response = restClient.placeGttOrder(
                type, quantity, product, instrumentKey, transactionType, rules);
        return extractGttOrderId(response);
    }

    /**
     * Modifies an existing GTT order.
     *
     * @param gttOrderId the GTT order ID
     * @param type       SINGLE or MULTIPLE
     * @param quantity   order quantity
     * @param rules      updated rules
     * @return the GTT order ID
     */
    public String modifyGttOrder(
            String gttOrderId, String type, long quantity,
            List<UpstoxGttRule> rules) {
        JsonNode response = restClient.modifyGttOrder(gttOrderId, type, quantity, rules);
        return extractGttOrderId(response);
    }

    /**
     * Fetches a full GTT order with all details.
     *
     * @param gttOrderId the GTT order ID
     * @return full GTT order, or null if not found
     */
    public UpstoxGttOrder getGttOrderDetails(String gttOrderId) {
        JsonNode response = restClient.getGttOrder(gttOrderId);
        JsonNode data = response.get("data");
        if (data != null && data.isArray() && data.size() > 0) {
            return UpstoxGttOrder.fromJson(data.get(0));
        }
        return null;
    }

    /**
     * Lists all active GTT orders with full details.
     *
     * @return list of full GTT orders
     */
    public List<UpstoxGttOrder> listGttOrders() {
        JsonNode response = restClient.getGttOrder(null);
        List<UpstoxGttOrder> orders = new ArrayList<>();
        JsonNode data = response.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                orders.add(UpstoxGttOrder.fromJson(node));
            }
        }
        return List.copyOf(orders);
    }

    // ─── GttOrderProvider implementation ────────────────────────────────

    @Override
    public Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        String instrumentToken = instrumentResolver.requireInstrumentKey(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        String transactionType = request.side() == Side.BUY ? "BUY" : "SELL";
        String product = mapProduct(request.productType());

        List<UpstoxGttRule> rules = new ArrayList<>();

        // Entry rule based on order type
        String entryTriggerType;
        double entryTriggerPrice;
        if (request.orderType() == OrderType.STOP_LOSS || request.orderType() == OrderType.STOP_LOSS_MARKET) {
            entryTriggerType = "ABOVE";
            // For stop-loss orders, the trigger price is the price to cross
            entryTriggerPrice = PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue();
        } else {
            entryTriggerType = "IMMEDIATE";
            entryTriggerPrice = PriceMath.fromPaisa(request.pricePaisa()).doubleValue();
        }
        rules.add(new UpstoxGttRule("ENTRY", entryTriggerType, entryTriggerPrice, null, null));

        // Multi-leg: add target/exit rule if quantity2 is provided
        String gttType = "SINGLE";
        if ("MULTIPLE".equals(orderFlag) && quantity2 != null && quantity2 > 0) {
            gttType = "MULTIPLE";
            double targetPrice = price2Paisa != null ? PriceMath.fromPaisa(price2Paisa).doubleValue() : 0.0;
            rules.add(new UpstoxGttRule("TARGET", "IMMEDIATE", targetPrice, null, null));
        }

        JsonNode response = restClient.placeGttOrder(
                gttType, request.quantity(), product,
                instrumentToken, transactionType, rules);
        String gttOrderId = extractGttOrderId(response);

        return toGttOrder(gttOrderId, request, rules);
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        // Map legName to strategy type for rules update
        String strategy = switch (legName != null ? legName.toUpperCase() : "") {
            case "ENTRY" -> "ENTRY";
            case "TARGET" -> "TARGET";
            default -> "ENTRY";
        };
        String triggerType = triggerPricePaisa > 0 ? "ABOVE" : "IMMEDIATE";
        double triggerPrice = triggerPricePaisa > 0
                ? PriceMath.fromPaisa(triggerPricePaisa).doubleValue()
                : PriceMath.fromPaisa(pricePaisa).doubleValue();
        List<UpstoxGttRule> rules = List.of(
                new UpstoxGttRule(strategy, triggerType, triggerPrice, null, null));
        String gttOrderId = modifyGttOrder(orderId, "SINGLE", quantity, rules);
        return new Order(
                orderId,
                gttOrderId,
                "",
                com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                Side.BUY,
                ProductType.INTRADAY,
                OrderType.LIMIT,
                OrderStatus.PENDING,
                quantity,
                0L,
                pricePaisa,
                triggerPricePaisa,
                System.currentTimeMillis(),
                ""
        );
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        return deleteAlert(orderId);
    }

    @Override
    public List<Order> getForeverOrders() {
        List<UpstoxGttOrder> gttOrders = listGttOrders();
        List<Order> orders = new ArrayList<>();
        for (UpstoxGttOrder gtt : gttOrders) {
            orders.add(toOrder(gtt));
        }
        return List.copyOf(orders);
    }

    // ─── GTT → Order mapping ───────────────────────────────────────────

    private static Order toGttOrder(String gttOrderId, OrderRequest request, List<UpstoxGttRule> rules) {
        // Extract trigger price from entry rule
        double entryPrice = 0.0;
        for (UpstoxGttRule rule : rules) {
            if ("ENTRY".equals(rule.strategy())) {
                entryPrice = rule.triggerPrice();
                break;
            }
        }
        return new Order(
                gttOrderId,
                request.correlationId(),
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.productType(),
                request.orderType(),
                OrderStatus.PENDING,
                request.quantity(),
                0L,
                request.pricePaisa(),
                request.triggerPricePaisa(),
                System.currentTimeMillis(),
                ""
        );
    }

    private static Order toOrder(UpstoxGttOrder gtt) {
        // Map rules to determine price and trigger
        double price = 0.0;
        double triggerPrice = 0.0;
        OrderStatus status = OrderStatus.UNKNOWN;
        Side side = Side.BUY;
        OrderType orderType = OrderType.LIMIT;

        for (UpstoxGttRule rule : gtt.rules()) {
            if ("ENTRY".equals(rule.strategy())) {
                price = rule.triggerPrice();
                triggerPrice = rule.triggerPrice();
                if ("ABOVE".equals(rule.triggerType())) {
                    orderType = OrderType.STOP_LOSS;
                }
                if (rule.triggerPrice() >= 0) {
                    status = OrderStatus.PENDING;
                }
            }
        }

        // Determine side from transaction type (not directly available in GTT order,
        // but we can infer from the entry rule)
        return new Order(
                gtt.gttOrderId(),
                "",
                gtt.tradingSymbol(),
                gtt.exchangeSegment(),
                side,
                mapProductToDomain(gtt.product()),
                orderType,
                status,
                gtt.quantity(),
                0L,
                (long) (price * 100),
                (long) (triggerPrice * 100),
                gtt.createdAt(),
                ""
        );
    }

    private static ProductType mapProductToDomain(String product) {
        return switch (product != null ? product.toUpperCase() : "") {
            case "D" -> ProductType.CNC;
            case "I" -> ProductType.INTRADAY;
            case "MTF" -> ProductType.MARGIN;
            default -> ProductType.INTRADAY;
        };
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static String extractGttOrderId(JsonNode response) {
        JsonNode data = response.get("data");
        if (data != null && data.has("gtt_order_ids") && data.get("gtt_order_ids").isArray()) {
            return data.get("gtt_order_ids").get(0).asText();
        }
        return "";
    }

    private static String extractOverallStatus(JsonNode order) {
        JsonNode rules = order.get("rules");
        if (rules != null && rules.isArray()) {
            for (JsonNode rule : rules) {
                if (rule.has("status")) {
                    return rule.get("status").asText();
                }
            }
        }
        return "UNKNOWN";
    }

    private static String extractStatusMessage(JsonNode order) {
        JsonNode rules = order.get("rules");
        if (rules != null && rules.isArray()) {
            for (JsonNode rule : rules) {
                if (rule.has("message") && !rule.get("message").isNull()
                        && !rule.get("message").asText().isBlank()) {
                    return rule.get("message").asText();
                }
            }
        }
        return "";
    }

    private static String mapProduct(com.tradej.core.domain.value.ProductType pt) {
        return switch (pt) {
            case INTRADAY -> "I";
            case CNC -> "D";
            case CARRY_FORWARD -> "D";
            case MARGIN -> "MTF";
        };
    }
}
