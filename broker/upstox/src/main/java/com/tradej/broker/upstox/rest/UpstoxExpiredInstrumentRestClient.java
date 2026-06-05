package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.resilience.UpstoxRetryExecutor;

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
    private final UpstoxRetryExecutor retryExecutor;

    public UpstoxExpiredInstrumentRestClient(
            UpstoxJsonHttpClient httpClient,
            UpstoxRetryExecutor retryExecutor
    ) {
        this.httpClient = httpClient;
        this.retryExecutor = retryExecutor;
    }

    public JsonNode getExpiredExpiries(String underlyingInstrumentKey) {
        return retryExecutor.execute(
                UpstoxRetryExecutor.CATEGORY_EXPIRED_INSTRUMENT,
                "expired-expiries",
                UpstoxRetryExecutor.EXPIRED_INSTRUMENT_POLICY,
                () -> httpClient.getJson(EXPIRED_EXPIRIES_PATH + "?instrument_key=" + encodeQuery(underlyingInstrumentKey))
        );
    }

    public JsonNode getExpiredOptionContracts(String underlyingInstrumentKey, LocalDate expiryDate) {
        String path = EXPIRED_OPTION_CONTRACT_PATH
                + "?instrument_key=" + encodeQuery(underlyingInstrumentKey)
                + "&expiry_date=" + expiryDate.format(DATE_FMT);
        return retryExecutor.execute(
                UpstoxRetryExecutor.CATEGORY_EXPIRED_INSTRUMENT,
                "expired-option-contracts",
                UpstoxRetryExecutor.EXPIRED_INSTRUMENT_POLICY,
                () -> httpClient.getJson(path));
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
        return retryExecutor.execute(
                UpstoxRetryExecutor.CATEGORY_EXPIRED_INSTRUMENT,
                "expired-historical-candle",
                UpstoxRetryExecutor.EXPIRED_INSTRUMENT_POLICY,
                () -> httpClient.getJson(path));
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
