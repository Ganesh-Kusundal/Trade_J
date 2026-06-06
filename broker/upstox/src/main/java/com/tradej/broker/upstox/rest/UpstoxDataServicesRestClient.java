package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.CHARGES_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.HOLIDAYS_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.PROFIT_LOSS_PATH;

/**
 * REST client for Upstox data services: charges, P&L reports, and market holidays.
 */
public final class UpstoxDataServicesRestClient {

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxDataServicesRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches trade charges/breakup.
     * GET /v2/trade/profit-loss/charges
     */
    public JsonNode getCharges() {
        return httpClient.getJson(CHARGES_PATH);
    }

    /**
     * Fetches trade-wise realized P&L report.
     * GET /v2/reports/profit-loss
     * Optional query params: from_date, to_date, segment, page_number, page_size
     */
    public JsonNode getProfitLoss() {
        return httpClient.getJson(PROFIT_LOSS_PATH);
    }

    /**
     * Fetches market holidays for the current year.
     * GET /v2/market/holidays
     */
    public JsonNode getHolidays() {
        return httpClient.getJson(HOLIDAYS_PATH);
    }
}
