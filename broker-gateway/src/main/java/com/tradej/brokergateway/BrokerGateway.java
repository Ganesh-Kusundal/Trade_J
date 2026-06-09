package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerRegistry;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;

import java.util.Set;

/**
 * Primary entry point for broker access.
 *
 * <p>Supports multiple brokers simultaneously. Each broker is identified by name:
 * "dhan", "upstox", "icici", "simulation".
 *
 * <p>Usage:
 * <pre>
 *   BrokerGateway gateway = BrokerGateway.create(dhanComposition);
 *   BrokerHandle dhan = gateway.broker("dhan");
 *   GatewayResult&lt;Quote&gt; result = dhan.quote("RELIANCE");
 * </pre>
 *
 * @see DefaultBrokerGateway the default implementation
 */
public interface BrokerGateway {

    /**
     * Get a handle for a specific broker by name.
     *
     * @param name "dhan", "upstox", "icici", or "simulation"
     */
    BrokerHandle broker(String name);

    /**
     * Get a handle for a specific broker by source enum.
     */
    BrokerHandle broker(BrokerSource source);

    /**
     * Returns the set of available broker sources.
     */
    Set<BrokerSource> availableBrokers();

    /**
     * Returns true if the named broker is available.
     */
    boolean hasBroker(String name);

    /**
     * Returns the first available broker handle (useful for single-broker setups).
     */
    BrokerHandle first();

    // ── Static Factory Methods ──────────────────────────────────────

    /**
     * Create a gateway from one or more broker compositions.
     */
    static BrokerGateway create(BrokerComposition... compositions) {
        return DefaultBrokerGateway.create(compositions);
    }

    /**
     * Create a gateway from a single named broker connection.
     */
    static BrokerGateway of(BrokerSource source, IBrokerConnection connection) {
        return DefaultBrokerGateway.of(source, connection);
    }

    /**
     * Create a gateway from multiple named broker connections.
     * Used by Spring DI to wire available broker beans.
     */
    static BrokerGateway fromConnections(java.util.Map<BrokerSource, IBrokerConnection> connections) {
        java.util.Map<BrokerSource, BrokerHandle> handles = new java.util.LinkedHashMap<>();
        connections.forEach((source, conn) -> handles.put(source, new BrokerHandle(source, conn)));
        return new DefaultBrokerGateway(handles);
    }

    /**
     * Convenience: create a Dhan-only gateway from config.
     */
    static BrokerGateway dhan(BrokerProfile.DhanConfig config) {
        return DefaultBrokerGateway.dhan(config);
    }

    /**
     * Create a gateway from a BrokerRegistry using the given profiles.
     * Each profile's broker type is matched to a registered BrokerProvider.
     */
    static BrokerGateway fromRegistry(BrokerRegistry registry, BrokerProfile... profiles) {
        return DefaultBrokerGateway.fromRegistry(registry, profiles);
    }
}
