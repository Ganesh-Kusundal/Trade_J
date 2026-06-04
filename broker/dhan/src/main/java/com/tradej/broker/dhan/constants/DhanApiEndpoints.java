package com.tradej.broker.dhan.constants;

/**
 * Centralised Dhan API endpoint URLs, path prefix patterns, and HTTP header names.
 *
 * <p>Consolidates hardcoded endpoint strings that were previously duplicated
 * across {@code DhanOptionsAdapter}, {@code DhanHistoricalDataClient},
 * {@code DhanInstrumentLoader}, and {@code DhanAuthenticatedHttpClient}.
 */
public final class DhanApiEndpoints {
    private DhanApiEndpoints() {
    }

    // ---- Base URLs ----

    /** Base REST API URL for Dhan v2 endpoints. */
    public static final String BASE_URL = "https://api.dhan.co/v2";

    /**
     * Option chain quote/greeks endpoint ({@code POST /v2/optionchain}).
     * Requires {@code UnderlyingScrip}, {@code UnderlyingSeg}, and {@code Expiry}.
     */
    public static final String OPTION_CHAIN_URL = BASE_URL + "/optionchain";

    /**
     * Option expiry list endpoint ({@code POST /v2/optionchain/expirylist}).
     * Requires {@code UnderlyingScrip} and {@code UnderlyingSeg} only.
     */
    public static final String OPTION_CHAIN_EXPIRY_LIST_URL = BASE_URL + "/optionchain/expirylist";

    /** Historical daily chart endpoint. */
    public static final String HISTORICAL_DAILY_URL = BASE_URL + "/charts/historical";

    /** Historical intraday chart endpoint. */
    public static final String HISTORICAL_INTRADAY_URL = BASE_URL + "/charts/intraday";

    /** Rolling option history endpoint for expired option analytics. */
    public static final String ROLLING_OPTION_URL = BASE_URL + "/charts/rollingoption";

    /** Margin calculator endpoint. */
    public static final String MARGIN_CALCULATOR_URL = BASE_URL + "/margin/calculator";

    /** PnL based session exit endpoint. */
    public static final String PNL_EXIT_URL = BASE_URL + "/pnlExit";

    /** Conditional alert order endpoint base. */
    public static final String ALERT_ORDERS_URL = BASE_URL + "/alerts/orders";

    /** Instrument master CSV download URL (hosted on Dhan images CDN). */
    public static final String INSTRUMENT_MASTER_URL = "https://images.dhan.co/api-data/api-scrip-master.csv";

    // ---- HTTP header names ----

    /** Header carrying the Dhan access token. */
    public static final String HEADER_ACCESS_TOKEN = "access-token";

    /** Header carrying the Dhan client ID. */
    public static final String HEADER_CLIENT_ID = "client-id";

    /** Accept header value for JSON responses. */
    public static final String HEADER_ACCEPT_JSON = "application/json";

    /** Content-Type header value for JSON request bodies. */
    public static final String HEADER_CONTENT_TYPE_JSON = "application/json";

    // ---- REST path prefixes (used by DhanRetryExecutor for rate-limit bucketing) ----

    /** Path prefix for order endpoints. */
    public static final String PATH_PREFIX_ORDERS = "/orders";

    /** Path prefix for alert endpoints. */
    public static final String PATH_PREFIX_ALERTS = "/alerts";

    /** Path prefix for margin endpoints. */
    public static final String PATH_PREFIX_MARGIN = "/margin";

    /** Path prefix for forever-orders endpoints. */
    public static final String PATH_PREFIX_FOREVER_ORDERS = "/forever-orders";

    /** Path prefix for super-order endpoints. */
    public static final String PATH_PREFIX_SUPER_ORDER = "/super-order";

    /** Path prefix for quote endpoints. */
    public static final String PATH_PREFIX_QUOTE = "/quote";

    /** Path prefix for option-chain endpoint. */
    public static final String PATH_PREFIX_OPTION_CHAIN = "/optionchain";

    /** Path prefix for holdings endpoint. */
    public static final String PATH_PREFIX_HOLDINGS = "/holdings";

    /** Path prefix for positions endpoint. */
    public static final String PATH_PREFIX_POSITIONS = "/positions";

    /** Path prefix for funds endpoint. */
    public static final String PATH_PREFIX_FUNDS = "/funds";

    /** Path prefix for profile endpoint. */
    public static final String PATH_PREFIX_PROFILE = "/profile";

    /** Path prefix for session PnL exit endpoint. */
    public static final String PATH_PREFIX_PNL_EXIT = "/pnlExit";
}
