package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import java.io.IOException;
import java.util.Map;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.CANCEL_ORDER_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.MODIFY_ORDER_PATH;
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
