package com.tradej.brokergateway.spi.impl;

import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.spi.BrokerHealthCheck;

import java.time.Duration;
import java.time.Instant;

/**
 * ICICI health check — verifies connectivity by calling LTP on RELIANCE.
 */
public final class IciciHealthCheck implements BrokerHealthCheck {

    @Override
    public HealthStatus check(BrokerHandle broker) {
        Instant start = Instant.now();
        try {
            var result = broker.ltp("RELIANCE");
            Duration latency = Duration.between(start, Instant.now());
            if (result.isSuccess() && result.data() > 0) {
                return HealthStatus.healthy(broker.source(), latency);
            }
            return HealthStatus.unhealthy(broker.source(), "LTP returned no data", latency);
        } catch (Exception ex) {
            Duration latency = Duration.between(start, Instant.now());
            return HealthStatus.unhealthy(broker.source(), ex.getMessage(), latency);
        }
    }
}
