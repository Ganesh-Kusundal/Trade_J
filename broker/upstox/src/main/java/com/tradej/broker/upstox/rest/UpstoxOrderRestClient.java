package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.CANCEL_ORDER_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.MODIFY_ORDER_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.MULTI_ORDER_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.ORDER_DETAILS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.ORDER_HISTORY_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.PLACE_ORDER_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.TRADES_PATH;

/**
 * REST client for Upstox order placement.
 */
public final class UpstoxOrderRestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxOrderRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode placeOrder(Map<String, Object> payload) {
        return postJson(PLACE_ORDER_PATH, payload);
    }

    public JsonNode modifyOrder(Map<String, Object> payload) {
        return postJsonBody(MODIFY_ORDER_PATH, payload, true);
    }

    public JsonNode cancelOrder(String orderId) {
        return httpClient.deleteJson(
                CANCEL_ORDER_PATH + "?order_id=" + java.net.URLEncoder.encode(orderId, java.nio.charset.StandardCharsets.UTF_8));
    }

    public JsonNode getOrderDetails(String orderId) {
        return httpClient.getJson(ORDER_DETAILS_PATH + "?order_id=" + orderId);
    }

    public JsonNode getOrderBook() {
        return httpClient.getJson(ORDER_HISTORY_PATH);
    }

    public JsonNode getTrades() {
        return httpClient.getJson(TRADES_PATH);
    }

    /**
     * Places multiple orders in a single API call (basket order).
     * Uses POST /v2/order/multi with an array of order payloads.
     *
     * @param orders list of order payloads (each as returned by UpstoxDomainMapper.toPlaceOrderPayload)
     * @return JSON response with array of order results
     */
    public JsonNode placeMultiOrder(List<Map<String, Object>> orders) {
        try {
            String body = MAPPER.writeValueAsString(orders);
            return httpClient.postJson(MULTI_ORDER_PATH, body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize multi-order payload", e);
        }
    }

    /**
     * Cancels multiple orders in a single API call.
     * Uses DELETE /v2/order/multi with a JSON body containing order IDs.
     *
     * @param orderIds list of order IDs to cancel
     * @return JSON response
     */
    public JsonNode cancelMultiOrder(List<String> orderIds) {
        try {
            var payload = MAPPER.createObjectNode();
            var idsArray = MAPPER.createArrayNode();
            for (String id : orderIds) {
                idsArray.add(id);
            }
            payload.set("order_ids", idsArray);
            return httpClient.deleteJson(MULTI_ORDER_PATH, MAPPER.writeValueAsString(payload));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize multi-order cancel payload", e);
        }
    }

    private JsonNode postJson(String path, Map<String, Object> payload) {
        return postJsonBody(path, payload, false);
    }

    private JsonNode postJsonBody(String path, Map<String, Object> payload, boolean put) {
        try {
            String body = MAPPER.writeValueAsString(payload);
            return put ? httpClient.putJson(path, body) : httpClient.postJson(path, body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Upstox order payload", e);
        }
    }
}
