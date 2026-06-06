package com.tradej.brokergateway;

import com.tradej.brokergateway.result.BrokerSource;

/**
 * Routes market data requests to the active broker.
 *
 * <p>Currently supports a single active broker. Future versions will support:
 * <ul>
 *   <li>Failover — automatic switching when the active broker becomes unavailable</li>
 *   <li>Load balancing — distribute requests across multiple brokers</li>
 *   <li>Capability routing — route option chain requests to the broker with best options support</li>
 * </ul>
 */
public final class BrokerRouter {

    private final BrokerGateway gateway;
    private volatile BrokerSource activeSource;

    public BrokerRouter(BrokerGateway gateway) {
        this.gateway = gateway;
        this.activeSource = gateway.availableBrokers().iterator().next();
    }

    public BrokerRouter(BrokerGateway gateway, BrokerSource initialActive) {
        this.gateway = gateway;
        this.activeSource = initialActive;
    }

    /**
     * Returns the currently active broker handle.
     */
    public BrokerHandle active() {
        return gateway.broker(activeSource);
    }

    /**
     * Returns the currently active broker source.
     */
    public BrokerSource activeSource() {
        return activeSource;
    }

    /**
     * Switch the active broker.
     */
    public void setActive(BrokerSource source) {
        if (!gateway.availableBrokers().contains(source)) {
            throw new IllegalArgumentException("Broker '" + source + "' not available");
        }
        this.activeSource = source;
    }

    /**
     * Switch the active broker by name.
     */
    public void setActive(String name) {
        setActive(BrokerSource.parse(name));
    }

    /**
     * Returns the underlying gateway.
     */
    public BrokerGateway gateway() {
        return gateway;
    }
}
