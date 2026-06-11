package com.tradej.broker.api.spi;

import com.tradej.broker.api.IBrokerConnection;

import java.util.Map;

/**
 * Service Provider Interface for broker implementations.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * Each provider knows how to create an {@link IBrokerConnection} for a specific broker.
 *
 * <p>To add a new broker:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Register in {@code META-INF/services/com.tradej.broker.api.spi.BrokerProvider}</li>
 * </ol>
 */
public interface BrokerProvider {

    /**
     * The broker source identifier (e.g., DHAN, UPSTOX, ICICI, SIMULATION).
     */
    BrokerSource source();

    /**
     * Human-readable display name (e.g., "DhanHQ", "Upstox", "ICICI Direct").
     */
    String displayName();

    /**
     * Describes the broker's capabilities and metadata.
     */
    BrokerDescriptor descriptor();

    /**
     * Creates a broker connection from the given configuration.
     *
     * @param configuration broker-specific configuration as key-value pairs
     * @return a connected IBrokerConnection
     */
    IBrokerConnection create(Map<String, Object> configuration);

    /**
     * Whether this provider is enabled and should be registered.
     * Default: true.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Semantic version of this broker provider plugin.
     * Default: "1.0.0"
     */
    default String version() {
        return "1.0.0";
    }
}
