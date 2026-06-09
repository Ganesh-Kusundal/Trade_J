package com.tradej.broker.core.metrics;

/**
 * Operational metrics for broker WebSocket connections.
 * Implementations should use Micrometer counters/gauges for Prometheus export.
 */
public interface BrokerWebSocketMetrics {

    BrokerWebSocketMetrics NOOP = new BrokerWebSocketMetrics() {
        @Override public void recordReconnect() {}
        @Override public void recordSubscriptionCount(int count) {}
        @Override public void recordMessageReceived() {}
        @Override public void recordParseError() {}
        @Override public void recordStaleDetected() {}
        @Override public void recordDroppedTick() {}
    };

    void recordReconnect();
    void recordSubscriptionCount(int count);
    void recordMessageReceived();
    void recordParseError();
    void recordStaleDetected();
    void recordDroppedTick();
}
