package com.tradej.broker.icici.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;

public final class BreezeOrderRestClient {
    private final BreezeAuthenticatedHttpClient httpClient;

    public BreezeOrderRestClient(BreezeAuthenticatedHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode placeOrder(ObjectNode payload) {
        return httpClient.postJson(BreezeApiEndpoints.ORDER, payload).successNode();
    }

    public JsonNode modifyOrder(ObjectNode payload) {
        return httpClient.putJson(BreezeApiEndpoints.ORDER, payload).successNode();
    }

    public JsonNode cancelOrder(ObjectNode payload) {
        return httpClient.deleteJson(BreezeApiEndpoints.ORDER, payload).successNode();
    }

    public JsonNode getOrderDetail(ObjectNode payload) {
        JsonNode success = httpClient.getJson(BreezeApiEndpoints.ORDER, payload).successNode();
        if (success.isArray() && !success.isEmpty()) {
            return success.get(0);
        }
        return success;
    }

    public JsonNode getOrderList(ObjectNode payload) {
        return httpClient.getJson(BreezeApiEndpoints.ORDER, payload).successNode();
    }

    public JsonNode getTrades(ObjectNode payload) {
        return httpClient.getJson(BreezeApiEndpoints.TRADES, payload).successNode();
    }
}
