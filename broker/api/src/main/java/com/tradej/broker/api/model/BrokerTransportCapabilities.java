package com.tradej.broker.api.model;

/**
 * Broker transport and trading surface capabilities (orthogonal to per-venue feed modes).
 */
public record BrokerTransportCapabilities(
        boolean supportsWebSocket,
        boolean supportsOrders,
        boolean supportsPortfolio,
        boolean supportsRestMarketData,
        boolean analyticsOnly
) {
    public static BrokerTransportCapabilities dhanLive() {
        return new BrokerTransportCapabilities(true, true, true, true, false);
    }

    public static BrokerTransportCapabilities upstoxTrading() {
        return new BrokerTransportCapabilities(true, true, true, true, false);
    }

    public static BrokerTransportCapabilities upstoxAnalytics() {
        return new BrokerTransportCapabilities(false, false, false, true, true);
    }
}
