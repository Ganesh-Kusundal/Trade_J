package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.upstox.domain.UpstoxGttRule;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import java.util.List;

/**
 * REST client for Upstox GTT (Good-Till-Triggered) order endpoints (v3).
 * <p>
 * GTT uses API v3 while the rest of the codebase uses v2. This client
 * derives the v3 base URL from the configured v2 base URL automatically.
 */
public final class UpstoxGttRestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxJsonHttpClient jsonHttpClient;
    private final String v3BaseUrl;

    public UpstoxGttRestClient(UpstoxJsonHttpClient jsonHttpClient) {
        this.jsonHttpClient = jsonHttpClient;
        // Derive v3 base URL from the configured v2 base URL
        // e.g. "https://api.upstox.com/v2" -> "https://api.upstox.com/v3"
        // Handles both "https://api.upstox.com/v2" and "https://api.upstox.com/v2/"
        String base = jsonHttpClient.baseUrl();
        this.v3BaseUrl = base.replace("/v2", "/v3").replaceAll("/+$", "");
    }

    /** Full GTT API base URL, derived from the v2 base. */
    public String v3BaseUrl() {
        return v3BaseUrl;
    }

    /**
     * Places a new GTT order.
     */
    public com.fasterxml.jackson.databind.JsonNode placeGttOrder(
            String type, long quantity, String product,
            String instrumentToken, String transactionType,
            List<UpstoxGttRule> rules) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("type", type);
        payload.put("quantity", quantity);
        payload.put("product", product);
        payload.put("instrument_token", instrumentToken);
        payload.put("transaction_type", transactionType);
        payload.set("rules", rulesToArray(rules));
        return jsonHttpClient.postUrlJson(v3BaseUrl + "/order/gtt/place", payload.toString());
    }

    /**
     * Modifies an existing GTT order.
     */
    public com.fasterxml.jackson.databind.JsonNode modifyGttOrder(
            String gttOrderId, String type, long quantity,
            List<UpstoxGttRule> rules) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("gtt_order_id", gttOrderId);
        payload.put("type", type);
        payload.put("quantity", quantity);
        payload.set("rules", rulesToArray(rules));
        return jsonHttpClient.putUrlJson(v3BaseUrl + "/order/gtt/modify", payload.toString());
    }

    /**
     * Cancels a GTT order via DELETE with JSON body.
     * Upstox API: DELETE /v3/order/gtt/cancel with {"gtt_order_id": "..."}
     */
    public com.fasterxml.jackson.databind.JsonNode cancelGttOrder(String gttOrderId) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = MAPPER.createObjectNode();
        payload.put("gtt_order_id", gttOrderId);
        return jsonHttpClient.deleteUrlJson(
                v3BaseUrl + "/order/gtt/cancel", payload.toString());
    }

    /**
     * Fetches details of a specific GTT order (or all orders if no ID given).
     */
    public com.fasterxml.jackson.databind.JsonNode getGttOrder(String gttOrderId) {
        if (gttOrderId != null && !gttOrderId.isBlank()) {
            return jsonHttpClient.getUrlJson(v3BaseUrl + "/order/gtt?gtt_order_id="
                    + java.net.URLEncoder.encode(gttOrderId, java.nio.charset.StandardCharsets.UTF_8));
        }
        return jsonHttpClient.getUrlJson(v3BaseUrl + "/order/gtt");
    }

    private static ArrayNode rulesToArray(List<UpstoxGttRule> rules) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (UpstoxGttRule rule : rules) {
            ObjectNode ruleNode = MAPPER.createObjectNode();
            ruleNode.put("strategy", rule.strategy());
            ruleNode.put("trigger_type", rule.triggerType());
            ruleNode.put("trigger_price", rule.triggerPrice());
            if (rule.trailingGap() != null) {
                ruleNode.put("trailing_gap", rule.trailingGap());
            }
            if (rule.marketProtection() != null) {
                ruleNode.put("market_protection", rule.marketProtection());
            }
            arr.add(ruleNode);
        }
        return arr;
    }
}
