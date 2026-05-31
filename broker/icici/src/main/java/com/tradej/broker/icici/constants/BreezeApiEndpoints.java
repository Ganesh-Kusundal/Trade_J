package com.tradej.broker.icici.constants;

public final class BreezeApiEndpoints {
    public static final String API_BASE = "https://api.icicidirect.com/breezeapi/api/v1/";
    /** v2 historical charts (supports {@code 1second}); GET with query params, no checksum. */
    public static final String API_V2_BASE = "https://breezeapi.icicidirect.com/api/v2/";
    public static final String SECURITY_MASTER_URL =
            "https://directlink.icicidirect.com/MotherAppMaster/SecurityMaster.zip";
    public static final String LIVE_STREAM_URL = "https://livestream.icicidirect.com";
    public static final String LIVE_FEEDS_URL = "https://livefeeds.icicidirect.com";
    public static final String LIVE_OHLC_STREAM_URL = "https://breezeapi.icicidirect.com";

    public static final String CUSTOMER_DETAILS = "customerdetails";
    public static final String FUNDS = "funds";
    public static final String QUOTES = "quotes";
    public static final String ORDER = "order";
    public static final String TRADES = "trades";
    public static final String HISTORICAL_CHARTS = "historicalcharts";
    public static final String PORTFOLIO_HOLDINGS = "portfolioholdings";
    public static final String PORTFOLIO_POSITIONS = "portfoliopositions";
    public static final String DEMAT_HOLDINGS = "dematholdings";
    public static final String OPTION_CHAIN = "optionchain";
    public static final String MARGIN_CALCULATOR = "margincalculator";
    public static final String SQUARE_OFF = "squareoff";

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_CHECKSUM = "X-Checksum";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_APP_KEY = "X-AppKey";
    public static final String HEADER_API_KEY = "apikey";
    public static final String HEADER_SESSION_TOKEN = "X-SessionToken";
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String USER_AGENT = "trade-j-breeze/1.0";

    private BreezeApiEndpoints() {
    }
}
