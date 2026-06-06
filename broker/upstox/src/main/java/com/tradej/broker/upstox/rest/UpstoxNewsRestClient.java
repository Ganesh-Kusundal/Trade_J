package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.NEWS_PATH;

/**
 * REST client for Upstox news endpoints.
 */
public final class UpstoxNewsRestClient {

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxNewsRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches news for specific instrument keys.
     *
     * @param instrumentKeys comma-separated instrument keys (max 30)
     * @param pageNumber page number (1-100)
     * @param pageSize page size (1-100)
     * @return JSON response
     */
    public JsonNode getNewsByInstrumentKeys(String instrumentKeys, int pageNumber, int pageSize) {
        String query = "category=instrument_keys&instrument_keys=" + encodeQuery(instrumentKeys)
                + "&page_number=" + pageNumber
                + "&page_size=" + pageSize;
        return getJson(NEWS_PATH + "?" + query);
    }

    /**
     * Fetches news for all current positions.
     *
     * @param pageNumber page number (1-100)
     * @param pageSize page size (1-100)
     * @return JSON response
     */
    public JsonNode getNewsForPositions(int pageNumber, int pageSize) {
        String query = "category=positions&page_number=" + pageNumber + "&page_size=" + pageSize;
        return getJson(NEWS_PATH + "?" + query);
    }

    /**
     * Fetches news for all holdings.
     *
     * @param pageNumber page number (1-100)
     * @param pageSize page size (1-100)
     * @return JSON response
     */
    public JsonNode getNewsForHoldings(int pageNumber, int pageSize) {
        String query = "category=holdings&page_number=" + pageNumber + "&page_size=" + pageSize;
        return getJson(NEWS_PATH + "?" + query);
    }

    private JsonNode getJson(String path) {
        return httpClient.getJson(path);
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}