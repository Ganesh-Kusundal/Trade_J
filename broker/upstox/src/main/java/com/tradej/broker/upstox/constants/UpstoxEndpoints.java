package com.tradej.broker.upstox.constants;

/**
 * All Upstox API endpoint paths and header names.
 */
public final class UpstoxEndpoints {
    private UpstoxEndpoints() {
    }

    // ─── Auth ────────────────────────────────────────────────────────────────
    public static final String AUTH_DIALOG_PATH     = "/login/authorization/dialog";
    public static final String AUTH_TOKEN_PATH       = "/login/authorization/token";
    public static final String LOGOUT_PATH           = "/logout";
    public static final String USER_PROFILE_PATH     = "/user/profile";

    // ─── Market Data ─────────────────────────────────────────────────────────
    public static final String MARKET_STATUS_PATH    = "/market/status/NSE";
    public static final String LTP_PATH              = "/market-quote/ltp";
    public static final String QUOTE_PATH            = "/market-quote/quotes";
    public static final String ORDER_BOOK_PATH       = "/market-quote/order-book";
    public static final String OHLC_PATH             = "/market-quote/ohlc";
    public static final String HISTORICAL_CANDLE_PATH = "/historical-candle/{instrumentKey}/{interval}/{toDate}/{fromDate}";

    // ─── WebSocket ─────────────────────────────────────────────────────────
    public static final String FEED_AUTHORIZE_PATH   = "/feed/market-data-feed/authorize";
    public static final String PORTFOLIO_STREAM_AUTHORIZE_PATH = "/feed/portfolio-stream-feed/authorize";

    // ─── Orders ──────────────────────────────────────────────────────────────
    public static final String PLACE_ORDER_PATH      = "/order/place";
    public static final String MODIFY_ORDER_PATH     = "/order/modify";
    public static final String CANCEL_ORDER_PATH     = "/order/cancel";
    public static final String MULTI_ORDER_PATH      = "/order/multi";
    public static final String ORDER_DETAILS_PATH    = "/order/details";
    public static final String ORDER_HISTORY_PATH    = "/order/history";
    public static final String TRADES_PATH           = "/order/trades/get-trades-for-day";

    // ─── Portfolio ─────────────────────────────────────────────────────────
    public static final String POSITIONS_PATH        = "/portfolio/short-term-positions";
    public static final String HOLDINGS_PATH          = "/portfolio/long-term-holdings";
    public static final String FUNDS_PATH             = "/user/get-funds-and-margin";
    public static final String CONVERT_POSITION_PATH  = "/portfolio/convert-position";

    // ─── Options ────────────────────────────────────────────────────────────
    public static final String OPTION_CONTRACTS_PATH  = "/option/contracts";
    public static final String OPTION_CHAIN_PATH      = "/option/chain";
    public static final String OPTION_EXPIRY_PATH     = "/option/expiry";
    public static final String OPTION_GREEKS_PATH     = "/option/greeks";

    // ─── Expired instruments (Plus plan) ───────────────────────────────────
    public static final String EXPIRED_EXPIRIES_PATH = "/expired-instruments/expiries";
    public static final String EXPIRED_OPTION_CONTRACT_PATH = "/expired-instruments/option/contract";
    public static final String EXPIRED_HISTORICAL_CANDLE_PATH =
            "/expired-instruments/historical-candle/{expiredInstrumentKey}/{interval}/{toDate}/{fromDate}";

    // ─── Margin ──────────────────────────────────────────────────────────
    public static final String MARGIN_REQUIREMENT_PATH = "/margin/requirement";

    // ─── P&L / Charges ──────────────────────────────────────────────────
    public static final String CHARGES_PATH        = "/trade/profit-loss/charges";
    public static final String PROFIT_LOSS_PATH     = "/reports/profit-loss";

    // ─── Market ─────────────────────────────────────────────────────────
    public static final String HOLIDAYS_PATH        = "/market/holidays";

    // ─── News ───────────────────────────────────────────────────────────
    public static final String NEWS_PATH = "/news";

    // ─── Instrument ────────────────────────────────────────────────────────
    public static final String INSTRUMENT_MASTER_PATH = "/instrument/master/{segment}";

    // ─── HTTP Headers ──────────────────────────────────────────────────────
    public static final String HEADER_AUTHORIZATION  = "Authorization";
    public static final String HEADER_CONTENT_TYPE   = "Content-Type";
    public static final String HEADER_ACCEPT         = "Accept";
    public static final String BEARER_PREFIX         = "Bearer ";
}
