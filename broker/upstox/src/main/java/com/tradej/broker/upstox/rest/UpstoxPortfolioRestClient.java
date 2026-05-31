package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.FUNDS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.HOLDINGS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.POSITIONS_PATH;

/**
 * REST client for Upstox portfolio endpoints.
 */
public final class UpstoxPortfolioRestClient {

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxPortfolioRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getPositions() {
        return httpClient.getJson(POSITIONS_PATH);
    }

    public JsonNode getHoldings() {
        return httpClient.getJson(HOLDINGS_PATH);
    }

    public JsonNode getFundsAndMargin() {
        return httpClient.getJson(FUNDS_PATH);
    }
}
