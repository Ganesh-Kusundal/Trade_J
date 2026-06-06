package com.tradej.brokergateway.result;

/**
 * Identifies which broker produced a result.
 */
public enum BrokerSource {
    DHAN,
    UPSTOX,
    ICICI,
    SIMULATION;

    public static BrokerSource parse(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Broker source name must not be blank");
        }
        return valueOf(name.trim().toUpperCase());
    }
}
