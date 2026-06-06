package com.tradej.broker.icici.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.http.BreezeJsonResponse;
import com.tradej.core.domain.model.InstrumentKey;

public final class BreezeMarketDataRestClient {
    private final BreezeAuthenticatedHttpClient httpClient;

    public BreezeMarketDataRestClient(BreezeAuthenticatedHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getQuotes(ObjectNode payload) {
        return requireSuccess(httpClient.getJson(BreezeApiEndpoints.QUOTES, payload));
    }

    /**
     * Fetches full order-book depth for an instrument.
     * Returns the JSON node containing depth data (array of bids/asks with price/qty).
     */
    public JsonNode getDepth(ObjectNode payload) {
        return requireSuccess(httpClient.getJson(BreezeApiEndpoints.QUOTES, payload));
    }

    private JsonNode requireSuccess(BreezeJsonResponse response) {
        JsonNode success = response.successNode();
        if (success.isArray() && !success.isEmpty()) {
            return success.get(0);
        }
        return success;
    }
}
