package com.tradej.brokergateway.spi;

import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.result.BrokerSource;

import java.time.Duration;

/**
 * Health check interface for broker plugins.
 * Implementations perform lightweight connectivity and auth checks
 * to determine if a broker is operational.
 */
public interface BrokerHealthCheck {

    /**
     * Result of a health check.
     */
    record HealthStatus(
            BrokerSource source,
            boolean healthy,
            String message,
            Duration latency,
            long timestampMs
    ) {
        public static HealthStatus healthy(BrokerSource source, Duration latency) {
            return new HealthStatus(source, true, "OK", latency, System.currentTimeMillis());
        }

        public static HealthStatus unhealthy(BrokerSource source, String message, Duration latency) {
            return new HealthStatus(source, false, message, latency, System.currentTimeMillis());
        }
    }

    /**
     * Perform a health check against the given broker handle.
     * Should be lightweight — e.g. LTP call, auth check, catalog loaded check.
     */
    HealthStatus check(BrokerHandle broker);
}
