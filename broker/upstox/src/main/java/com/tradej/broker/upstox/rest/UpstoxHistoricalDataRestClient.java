package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.resilience.UpstoxResilienceExecutor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.HISTORICAL_CANDLE_PATH;

/**
 * REST client for Upstox historical candle data.
 * <p>
 * Upstox URL format: {@code /historical-candle/{instrumentKey}/{interval}/{toDate}/{fromDate}}
 * <br>
 * Note: the API expects {@code toDate} <em>before</em> {@code fromDate} in the path.
 */
public final class UpstoxHistoricalDataRestClient {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UpstoxJsonHttpClient httpClient;
    private final UpstoxResilienceExecutor resilienceExecutor;

    public UpstoxHistoricalDataRestClient(UpstoxJsonHttpClient httpClient) {
        this(httpClient, null);
    }

    public UpstoxHistoricalDataRestClient(
            UpstoxJsonHttpClient httpClient,
            UpstoxResilienceExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.resilienceExecutor = resilienceExecutor;
    }

    /**
     * Fetches historical candles for the given instrument key, interval, and date range.
     *
     * @param instrumentKey e.g. "NSE_EQ|INE002A01018"
     * @param interval      e.g. "1minute", "30minute", "day", "week", "month"
     * @param fromDate      start date
     * @param toDate        end date
     */
    public JsonNode getHistoricalCandles(String instrumentKey, String interval, LocalDate fromDate, LocalDate toDate) {
        String path = HISTORICAL_CANDLE_PATH
                .replace("{instrumentKey}", encodePathSegment(instrumentKey))
                .replace("{interval}", encodePathSegment(interval))
                .replace("{toDate}", toDate.format(DATE_FMT))
                .replace("{fromDate}", fromDate.format(DATE_FMT));
        if (resilienceExecutor == null) {
            return httpClient.getJson(path);
        }
        return resilienceExecutor.executeData(
                "historical-candle",
                () -> httpClient.getJson(path)
        );
    }

    // Date windowing limits (max days per API call)
    public static final int DAILY_MAX_DAYS = 365;
    public static final int INTRADAY_MAX_DAYS = 90;

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
