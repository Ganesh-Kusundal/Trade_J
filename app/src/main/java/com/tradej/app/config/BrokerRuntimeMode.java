package com.tradej.app.config;

/**
 * High-level broker runtime transport profile used for startup and health policies.
 */
public enum BrokerRuntimeMode {
    DHAN_LIVE_WS,
    DHAN_SANDBOX,
    UPSTOX_TRADING_WS,
    UPSTOX_ANALYTICS_REST,
    ICICI_TRADING_WS,
    BROKER_GATEWAY;

    public boolean isUpstox() {
        return this == UPSTOX_TRADING_WS || this == UPSTOX_ANALYTICS_REST;
    }

    public boolean isIcici() {
        return this == ICICI_TRADING_WS;
    }

    public boolean isAnalyticsRest() {
        return this == UPSTOX_ANALYTICS_REST;
    }

    public boolean expectsWebSocket() {
        return this == DHAN_LIVE_WS
                || this == UPSTOX_TRADING_WS
                || this == ICICI_TRADING_WS
                || this == BROKER_GATEWAY;
    }
}
