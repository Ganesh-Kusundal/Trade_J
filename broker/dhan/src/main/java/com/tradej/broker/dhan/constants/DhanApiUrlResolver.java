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
        return baseUrl + "/optionchain";
    }

    public String optionChainExpiryListUrl() {
        return baseUrl + "/optionchain/expirylist";
    }

    public String historicalDailyUrl() {
        return baseUrl + "/charts/historical";
    }

    public String historicalIntradayUrl() {
        return baseUrl + "/charts/intraday";
    }

    public String rollingOptionUrl() {
        return baseUrl + "/charts/rollingoption";
    }

    public String marginCalculatorUrl() {
        return baseUrl + "/margincalculator";
    }

    public String pnlExitUrl() {
        return baseUrl + "/pnlExit";
    }

    public String alertOrdersUrl() {
        return baseUrl + "/alerts/orders";
    }

    public String ordersUrl() {
        return baseUrl + "/orders";
    }

    public String orderUrl(String orderId) {
        return ordersUrl() + "/" + orderId;
    }

    public String tradesUrl() {
        return baseUrl + "/trades";
    }

    public String superOrderUrl() {
        return baseUrl + "/super-order";
    }

    public String foreverOrdersUrl() {
        return baseUrl + (sandboxBaseUrl() ? "/forever-orders" : "/forever/orders");
    }

    public String foreverOrderUrl(String orderId) {
        return foreverOrdersUrl() + "/" + orderId;
    }

    public String foreverOrdersAllUrl() {
        return baseUrl + (sandboxBaseUrl() ? "/forever-orders" : "/forever/all");
    }

    public String orderByCorrelationIdUrl(String correlationId) {
        return ordersUrl() + "/external/" + correlationId;
    }

    public String tradesUrlForOrder(String orderId) {
        return tradesUrl() + "/" + orderId;
    }

    public String superOrdersListUrl() {
        return baseUrl + (sandboxBaseUrl() ? "/super-order" : "/super/orders");
    }

    public String superOrderByIdUrl(String orderId) {
        return superOrdersListUrl() + "/" + orderId;
    }

    public String superOrderLegUrl(String orderId, String legName) {
        return superOrderByIdUrl(orderId) + "/" + legName;
    }

    public String killSwitchUrl() {
        return baseUrl + "/killswitch";
    }

    public String ledgerUrl() {
        return baseUrl + "/ledger";
    }

    public String edisTpinUrl() {
        return baseUrl + "/edis/tpin";
    }

    public String edisFormUrl() {
        return baseUrl + "/edis/form";
    }

    public String edisInquiryUrl(String isin) {
        return baseUrl + "/edis/inquire/" + isin;
    }

    public String profileUrl() {
        return baseUrl + "/fundlimit/userprofile";
    }

    private boolean sandboxBaseUrl() {
        return baseUrl.contains("sandbox");
    }

    public String sliceOrderUrl() {
        return baseUrl + "/orders/slicing";
    }

    public String fundLimitUrl() {
        return baseUrl + "/fundlimit";
    }

    public String positionsUrl() {
        return baseUrl + "/positions";
    }

    public String holdingsUrl() {
        return baseUrl + "/holdings";
    }

    public String marketFeedLtpUrl() {
        return baseUrl + "/marketfeed/ltp";
    }

    public String marketFeedQuoteUrl() {
        return baseUrl + "/marketfeed/quote";
    }
}
