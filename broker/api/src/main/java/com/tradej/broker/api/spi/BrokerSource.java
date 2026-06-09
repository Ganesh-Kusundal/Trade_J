package com.tradej.broker.api.spi;

/**
 * Identifies which broker produced a result or which broker to connect to.
 * 
 * <p>Part of the broker SPI layer - belongs in broker-api as it's a core contract.
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
