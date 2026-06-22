package com.tradej.broker.api.capability;

import com.tradej.broker.api.IBrokerConnection;

import java.util.Objects;
import java.util.Optional;

/**
 * Single boundary for resolving optional broker capabilities.
 */
public final class BrokerCapabilityRouter {

    private final IBrokerConnection connection;
    private final String brokerName;

    private BrokerCapabilityRouter(IBrokerConnection connection, String brokerName) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.brokerName = brokerName == null || brokerName.isBlank() ? "Broker" : brokerName;
    }

    public static BrokerCapabilityRouter forConnection(IBrokerConnection connection) {
        return new BrokerCapabilityRouter(connection, "Broker");
    }

    public static BrokerCapabilityRouter named(String brokerName, IBrokerConnection connection) {
        return new BrokerCapabilityRouter(connection, brokerName);
    }

    public IBrokerConnection connection() {
        return connection;
    }

    public <T> Optional<T> find(Class<T> capabilityClass) {
        Objects.requireNonNull(capabilityClass, "capabilityClass");
        return connection.getCapability(capabilityClass);
    }

    public <T> T require(Class<T> capabilityClass, String featureName) {
        return find(capabilityClass)
                .orElseThrow(() -> new UnsupportedOperationException(
                        brokerName + " does not support " + capabilityName(capabilityClass, featureName)));
    }

    public boolean supports(Class<?> capabilityClass) {
        Objects.requireNonNull(capabilityClass, "capabilityClass");
        return connection.getCapability(capabilityClass).isPresent();
    }

    private static String capabilityName(Class<?> capabilityClass, String featureName) {
        return featureName == null || featureName.isBlank()
                ? capabilityClass.getSimpleName()
                : featureName;
    }
}
