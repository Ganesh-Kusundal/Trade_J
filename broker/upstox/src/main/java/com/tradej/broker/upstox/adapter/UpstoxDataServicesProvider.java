package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.rest.UpstoxDataServicesRestClient;

/**
 * Provider for Upstox data services: trade charges, P&L reports, and market holidays.
 */
public final class UpstoxDataServicesProvider {

    private final UpstoxDataServicesRestClient restClient;

    public UpstoxDataServicesProvider(UpstoxDataServicesRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Returns trade charges/breakup data.
     */
    public JsonNode getCharges() {
        return restClient.getCharges();
    }

    /**
     * Returns trade-wise realized P&L report.
     */
    public JsonNode getProfitLoss() {
        return restClient.getProfitLoss();
    }

    /**
     * Returns market holidays for the current year.
     */
    public JsonNode getHolidays() {
        return restClient.getHolidays();
    }
}
