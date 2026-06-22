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

    public static final String PATH_OPTION_CHAIN = "/optionchain";
    public static final String PATH_OPTION_CHAIN_EXPIRY_LIST = "/optionchain/expirylist";
    public static final String PATH_HISTORICAL_DAILY = "/charts/historical";
    public static final String PATH_HISTORICAL_INTRADAY = "/charts/intraday";
    public static final String PATH_ROLLING_OPTION = "/charts/rollingoption";
    public static final String PATH_MARGIN_CALCULATOR = "/margincalculator";
    public static final String PATH_PNL_EXIT = "/pnlExit";
    public static final String PATH_ALERT_ORDERS = "/alerts/orders";
    public static final String PATH_MARKET_FEED_LTP = "/marketfeed/ltp";
    public static final String PATH_MARKET_FEED_OHLC = "/marketfeed/ohlc";
    public static final String PATH_MARKET_FEED_QUOTE = "/marketfeed/quote";
    public static final String PATH_POSITIONS = "/positions";
    public static final String PATH_HOLDINGS = "/holdings";
    public static final String PATH_FUND_LIMIT = "/fundlimit";
    public static final String PATH_PROFILE = "/profile";
    public static final String PATH_ORDERS = "/orders";
    public static final String PATH_TRADES = "/trades";
    public static final String PATH_SUPER_ORDER_SANDBOX = "/super-order";
    public static final String PATH_SUPER_ORDERS_LIVE = "/super/orders";
    public static final String PATH_FOREVER_ORDERS_SANDBOX = "/forever-orders";
    public static final String PATH_FOREVER_ORDERS_LIVE = "/forever/orders";
    public static final String PATH_FOREVER_ORDERS_ALL_LIVE = "/forever/all";
    public static final String PATH_KILL_SWITCH = "/killswitch";
    public static final String PATH_LEDGER = "/ledger";
    public static final String PATH_EDIS_TPIN = "/edis/tpin";
    public static final String PATH_EDIS_FORM = "/edis/form";
    public static final String PATH_EDIS_INQUIRE = "/edis/inquire";
    public static final String PATH_SLICE_ORDER = "/orders/slicing";

    /**
     * Option chain quote/greeks endpoint ({@code POST /v2/optionchain}).
     * Requires {@code UnderlyingScrip}, {@code UnderlyingSeg}, and {@code Expiry}.
     */
    public static final String OPTION_CHAIN_URL = BASE_URL + PATH_OPTION_CHAIN;

    /**
     * Option expiry list endpoint ({@code POST /v2/optionchain/expirylist}).
     * Requires {@code UnderlyingScrip} and {@code UnderlyingSeg} only.
     */
    public static final String OPTION_CHAIN_EXPIRY_LIST_URL = BASE_URL + PATH_OPTION_CHAIN_EXPIRY_LIST;

    /** Historical daily chart endpoint. */
    public static final String HISTORICAL_DAILY_URL = BASE_URL + PATH_HISTORICAL_DAILY;

    /** Historical intraday chart endpoint. */
    public static final String HISTORICAL_INTRADAY_URL = BASE_URL + PATH_HISTORICAL_INTRADAY;

    /** Rolling option history endpoint for expired option analytics. */
    public static final String ROLLING_OPTION_URL = BASE_URL + PATH_ROLLING_OPTION;

    /** Margin calculator endpoint. */
    public static final String MARGIN_CALCULATOR_URL = BASE_URL + PATH_MARGIN_CALCULATOR;

    /** PnL based session exit endpoint. */
    public static final String PNL_EXIT_URL = BASE_URL + PATH_PNL_EXIT;

    /** Conditional alert order endpoint base. */
    public static final String ALERT_ORDERS_URL = BASE_URL + PATH_ALERT_ORDERS;

    /** Instrument master CSV download URL (hosted on Dhan images CDN). */
    public static final String INSTRUMENT_MASTER_URL = "https://images.dhan.co/api-data/api-scrip-master.csv";

    /** Market quote ticker (LTP) endpoint. */
    public static final String TICKER_DATA_URL = BASE_URL + PATH_MARKET_FEED_LTP;

    /** Market quote OHLC endpoint. */
    public static final String OHLC_DATA_URL = BASE_URL + PATH_MARKET_FEED_OHLC;

    /** Market quote full snapshot endpoint. */
    public static final String QUOTE_DATA_URL = BASE_URL + PATH_MARKET_FEED_QUOTE;

    /** Default market feed WebSocket URL. */
    public static final String MARKET_FEED_WS_URL = "wss://api-feed.dhan.co";

    /** Default order update WebSocket URL. */
    public static final String ORDER_UPDATE_WS_URL = "wss://api-order-update.dhan.co";

    /** Positions endpoint. */
    public static final String POSITIONS_URL = BASE_URL + PATH_POSITIONS;

    /** Holdings endpoint. */
    public static final String HOLDINGS_URL = BASE_URL + PATH_HOLDINGS;

    /** Funds endpoint. */
    public static final String FUNDS_URL = BASE_URL + PATH_FUND_LIMIT;

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
    public static final String PATH_PREFIX_ORDERS = PATH_ORDERS;

    /** Path prefix for alert endpoints. */
    public static final String PATH_PREFIX_ALERTS = "/alerts";

    /** Path prefix for margin endpoints. */
    public static final String PATH_PREFIX_MARGIN = PATH_MARGIN_CALCULATOR;

    /** Path prefix for forever-orders endpoints. */
    public static final String PATH_PREFIX_FOREVER_ORDERS = PATH_FOREVER_ORDERS_SANDBOX;

    /** Path prefix for super-order endpoints. */
    public static final String PATH_PREFIX_SUPER_ORDER = PATH_SUPER_ORDER_SANDBOX;

    /** Path prefix for quote endpoints. */
    public static final String PATH_PREFIX_QUOTE = "/marketfeed";

    /** Path prefix for option-chain endpoint. */
    public static final String PATH_PREFIX_OPTION_CHAIN = PATH_OPTION_CHAIN;

    /** Path prefix for holdings endpoint. */
    public static final String PATH_PREFIX_HOLDINGS = PATH_HOLDINGS;

    /** Path prefix for positions endpoint. */
    public static final String PATH_PREFIX_POSITIONS = PATH_POSITIONS;

    /** Path prefix for funds endpoint. */
    public static final String PATH_PREFIX_FUNDS = PATH_FUND_LIMIT;

    /** Path prefix for profile endpoint. */
    public static final String PATH_PREFIX_PROFILE = PATH_PROFILE;

    /** Path prefix for session PnL exit endpoint. */
    public static final String PATH_PREFIX_PNL_EXIT = PATH_PNL_EXIT;
}
