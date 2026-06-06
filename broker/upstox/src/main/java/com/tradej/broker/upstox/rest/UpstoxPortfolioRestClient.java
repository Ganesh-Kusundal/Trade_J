package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.CONVERT_POSITION_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.FUNDS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.HOLDINGS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.POSITIONS_PATH;

/**
 * REST client for Upstox portfolio endpoints.
 */
public final class UpstoxPortfolioRestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Converts an existing position from one product type to another.
     * e.g. MIS (Intraday) to CNC (Delivery) or vice versa.
     *
     * @param instrumentToken the Upstox instrument key
     * @param oldProduct      current product type (MIS, CNC, NRML)
     * @param newProduct      desired product type (MIS, CNC, NRML)
     * @param transactionType BUY or SELL
     * @param quantity        quantity to convert
     * @return JSON response
     */
    public JsonNode convertPosition(
            String instrumentToken,
            String oldProduct,
            String newProduct,
            String transactionType,
            long quantity) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("instrument_token", instrumentToken);
        payload.put("old_product", oldProduct);
        payload.put("new_product", newProduct);
        payload.put("transaction_type", transactionType);
        payload.put("quantity", quantity);
        return httpClient.putJson(CONVERT_POSITION_PATH, payload.toString());
    }
}
