package com.tradej.broker.icici.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;

public final class BreezePortfolioRestClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BreezeAuthenticatedHttpClient httpClient;

    public BreezePortfolioRestClient(BreezeAuthenticatedHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getFunds() {
        return httpClient.getJson(BreezeApiEndpoints.FUNDS, empty()).successNode();
    }

    public JsonNode getDematHoldings() {
        return httpClient.getJson(BreezeApiEndpoints.DEMAT_HOLDINGS, empty()).successNode();
    }

    public JsonNode getPortfolioHoldings() {
        return httpClient.getJson(BreezeApiEndpoints.PORTFOLIO_HOLDINGS, empty()).successNode();
    }

    public JsonNode getPortfolioPositions() {
        return httpClient.getJson(BreezeApiEndpoints.PORTFOLIO_POSITIONS, empty()).successNode();
    }

    private ObjectNode empty() {
        return OBJECT_MAPPER.createObjectNode();
    }
}
