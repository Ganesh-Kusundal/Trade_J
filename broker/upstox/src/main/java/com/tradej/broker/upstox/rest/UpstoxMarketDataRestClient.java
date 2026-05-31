package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.*;

/**
 * REST client for Upstox market data endpoints.
 */
public final class UpstoxMarketDataRestClient {

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxMarketDataRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches LTP for one or more instrument keys.
     */
    public JsonNode getLtp(String instrumentKeys) {
        return getJson(LTP_PATH + "?instrument_key=" + encodeQuery(instrumentKeys));
    }

    /**
     * Fetches full quote for one or more instrument keys.
     */
    public JsonNode getQuote(String instrumentKeys) {
        return getJson(QUOTE_PATH + "?instrument_key=" + encodeQuery(instrumentKeys));
    }

    /**
     * Fetches order book / market depth for an instrument key.
     */
    public JsonNode getDepth(String instrumentKey) {
        return getJson(ORDER_BOOK_PATH + "?instrument_key=" + encodeQuery(instrumentKey));
    }

    /**
     * Fetches OHLC snapshot for one or more instrument keys.
     */
    public JsonNode getOhlc(String instrumentKeys) {
        return getJson(OHLC_PATH + "?instrument_key=" + encodeQuery(instrumentKeys));
    }

    private JsonNode getJson(String path) {
        return httpClient.getJson(path);
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
