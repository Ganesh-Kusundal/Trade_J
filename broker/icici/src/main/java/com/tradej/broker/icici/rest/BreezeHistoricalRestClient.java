package com.tradej.broker.icici.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;

import java.util.Map;

public final class BreezeHistoricalRestClient {
    private final BreezeAuthenticatedHttpClient httpClient;

    public BreezeHistoricalRestClient(BreezeAuthenticatedHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getHistoricalCharts(ObjectNode payload) {
        return httpClient.getJson(BreezeApiEndpoints.HISTORICAL_CHARTS, payload).successNode();
    }

    /** v2 historical charts ({@code get_historical_data_v2}); supports {@code 1second}. */
    public JsonNode getHistoricalChartsV2(Map<String, String> queryParams) {
        return httpClient.getV2Json(BreezeApiEndpoints.HISTORICAL_CHARTS, queryParams).successNode();
    }
}
