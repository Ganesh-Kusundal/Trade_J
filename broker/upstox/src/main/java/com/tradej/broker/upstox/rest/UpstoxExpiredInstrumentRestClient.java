package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.resilience.UpstoxResilienceExecutor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.EXPIRED_EXPIRIES_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.EXPIRED_HISTORICAL_CANDLE_PATH;
import static com.tradej.broker.upstox.constants.UpstoxEndpoints.EXPIRED_OPTION_CONTRACT_PATH;

public final class UpstoxExpiredInstrumentRestClient {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UpstoxJsonHttpClient httpClient;
    private final UpstoxResilienceExecutor resilienceExecutor;

    public UpstoxExpiredInstrumentRestClient(
            UpstoxJsonHttpClient httpClient,
            UpstoxResilienceExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.resilienceExecutor = resilienceExecutor;
    }

    public JsonNode getExpiredExpiries(String underlyingInstrumentKey) {
        return resilienceExecutor.executeExpiredInstrument(
                "expired-expiries",
                () -> httpClient.getJson(EXPIRED_EXPIRIES_PATH + "?instrument_key=" + encodeQuery(underlyingInstrumentKey))
        );
    }

    public JsonNode getExpiredOptionContracts(String underlyingInstrumentKey, LocalDate expiryDate) {
        String path = EXPIRED_OPTION_CONTRACT_PATH
                + "?instrument_key=" + encodeQuery(underlyingInstrumentKey)
                + "&expiry_date=" + expiryDate.format(DATE_FMT);
        return resilienceExecutor.executeExpiredInstrument("expired-option-contracts", () -> httpClient.getJson(path));
    }

    public JsonNode getExpiredHistoricalCandles(
            String expiredInstrumentKey,
            String interval,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String path = EXPIRED_HISTORICAL_CANDLE_PATH
                .replace("{expiredInstrumentKey}", encodePath(expiredInstrumentKey))
                .replace("{interval}", encodePath(interval))
                .replace("{toDate}", toDate.format(DATE_FMT))
                .replace("{fromDate}", fromDate.format(DATE_FMT));
        return resilienceExecutor.executeExpiredInstrument("expired-historical-candle", () -> httpClient.getJson(path));
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
