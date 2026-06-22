package com.tradej.broker.dhan.constants;

import com.tradej.broker.dhan.config.DhanConnectionSettings;

public final class DhanApiUrlResolver {
    private final String baseUrl;

    public DhanApiUrlResolver(DhanConnectionSettings settings) {
        this(settings.restBaseUrl());
    }

    public DhanApiUrlResolver(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String optionChainUrl() {
        return baseUrl + DhanApiEndpoints.PATH_OPTION_CHAIN;
    }

    public String optionChainExpiryListUrl() {
        return baseUrl + DhanApiEndpoints.PATH_OPTION_CHAIN_EXPIRY_LIST;
    }

    public String historicalDailyUrl() {
        return baseUrl + DhanApiEndpoints.PATH_HISTORICAL_DAILY;
    }

    public String historicalIntradayUrl() {
        return baseUrl + DhanApiEndpoints.PATH_HISTORICAL_INTRADAY;
    }

    public String rollingOptionUrl() {
        return baseUrl + DhanApiEndpoints.PATH_ROLLING_OPTION;
    }

    public String marginCalculatorUrl() {
        return baseUrl + DhanApiEndpoints.PATH_MARGIN_CALCULATOR;
    }

    public String pnlExitUrl() {
        return baseUrl + DhanApiEndpoints.PATH_PNL_EXIT;
    }

    public String alertOrdersUrl() {
        return baseUrl + DhanApiEndpoints.PATH_ALERT_ORDERS;
    }

    public String ordersUrl() {
        return baseUrl + DhanApiEndpoints.PATH_ORDERS;
    }

    public String orderUrl(String orderId) {
        return ordersUrl() + "/" + orderId;
    }

    public String tradesUrl() {
        return baseUrl + DhanApiEndpoints.PATH_TRADES;
    }

    public String superOrderUrl() {
        return baseUrl + DhanApiEndpoints.PATH_SUPER_ORDER_SANDBOX;
    }

    public String foreverOrdersUrl() {
        return baseUrl + (sandboxBaseUrl()
                ? DhanApiEndpoints.PATH_FOREVER_ORDERS_SANDBOX
                : DhanApiEndpoints.PATH_FOREVER_ORDERS_LIVE);
    }

    public String foreverOrderUrl(String orderId) {
        return foreverOrdersUrl() + "/" + orderId;
    }

    public String foreverOrdersAllUrl() {
        return baseUrl + (sandboxBaseUrl()
                ? DhanApiEndpoints.PATH_FOREVER_ORDERS_SANDBOX
                : DhanApiEndpoints.PATH_FOREVER_ORDERS_ALL_LIVE);
    }

    public String orderByCorrelationIdUrl(String correlationId) {
        return ordersUrl() + "/external/" + correlationId;
    }

    public String tradesUrlForOrder(String orderId) {
        return tradesUrl() + "/" + orderId;
    }

    public String superOrdersListUrl() {
        return baseUrl + (sandboxBaseUrl()
                ? DhanApiEndpoints.PATH_SUPER_ORDER_SANDBOX
                : DhanApiEndpoints.PATH_SUPER_ORDERS_LIVE);
    }

    public String superOrderByIdUrl(String orderId) {
        return superOrdersListUrl() + "/" + orderId;
    }

    public String superOrderLegUrl(String orderId, String legName) {
        return superOrderByIdUrl(orderId) + "/" + legName;
    }

    public String killSwitchUrl() {
        return baseUrl + DhanApiEndpoints.PATH_KILL_SWITCH;
    }

    public String ledgerUrl() {
        return baseUrl + DhanApiEndpoints.PATH_LEDGER;
    }

    public String edisTpinUrl() {
        return baseUrl + DhanApiEndpoints.PATH_EDIS_TPIN;
    }

    public String edisFormUrl() {
        return baseUrl + DhanApiEndpoints.PATH_EDIS_FORM;
    }

    public String edisInquiryUrl(String isin) {
        return baseUrl + DhanApiEndpoints.PATH_EDIS_INQUIRE + "/" + isin;
    }

    public String profileUrl() {
        return baseUrl + DhanApiEndpoints.PATH_PROFILE;
    }

    private boolean sandboxBaseUrl() {
        return baseUrl.contains("sandbox");
    }

    public String sliceOrderUrl() {
        return baseUrl + DhanApiEndpoints.PATH_SLICE_ORDER;
    }

    public String fundLimitUrl() {
        return baseUrl + DhanApiEndpoints.PATH_FUND_LIMIT;
    }

    public String positionsUrl() {
        return baseUrl + DhanApiEndpoints.PATH_POSITIONS;
    }

    public String holdingsUrl() {
        return baseUrl + DhanApiEndpoints.PATH_HOLDINGS;
    }

    public String marketFeedLtpUrl() {
        return baseUrl + DhanApiEndpoints.PATH_MARKET_FEED_LTP;
    }

    public String marketFeedOhlcUrl() {
        return baseUrl + DhanApiEndpoints.PATH_MARKET_FEED_OHLC;
    }

    public String marketFeedQuoteUrl() {
        return baseUrl + DhanApiEndpoints.PATH_MARKET_FEED_QUOTE;
    }
}
