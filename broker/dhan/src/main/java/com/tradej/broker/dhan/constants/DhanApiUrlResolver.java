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
        return baseUrl + "/forever-orders";
    }

    public String sliceOrderUrl() {
        return baseUrl + "/orders/slicing";
    }

    public String fundLimitUrl() {
        return baseUrl + "/fundlimit";
    }
}
