package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.OPTION_CHAIN_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.OPTION_CONTRACTS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.OPTION_EXPIRY_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.OPTION_GREEKS_PATH;

/**
 * REST client for Upstox option chain endpoints.
 */
public final class UpstoxOptionChainRestClient {

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxOptionChainRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getOptionContracts(String instrumentKey) {
        return httpClient.getJson(OPTION_CONTRACTS_PATH + "?instrument_key=" + instrumentKey);
    }

    public JsonNode getOptionChain(String underlyingKey, String expiryDate) {
        return httpClient.getJson(OPTION_CHAIN_PATH + "?instrument_key=" + underlyingKey + "&expiry_date=" + expiryDate);
    }

    public JsonNode getExpiries(String underlyingKey) {
        return httpClient.getJson(OPTION_EXPIRY_PATH + "?instrument_key=" + underlyingKey);
    }

    public JsonNode getGreeks(String optionKey) {
        return httpClient.getJson(OPTION_GREEKS_PATH + "?instrument_key=" + optionKey);
    }
}
