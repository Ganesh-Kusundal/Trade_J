package com.tradej.app.config;

import com.tradej.broker.core.metrics.BrokerResilienceMetrics;
import com.tradej.broker.core.resilience.CircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that registers broker resilience metrics
 * with Micrometer and exposes the {@link CircuitBreakerMetrics} callback.
 */
@Configuration
public class BrokerResilienceMetricsConfiguration {

    @Bean
    public BrokerResilienceMetrics brokerResilienceMetrics(MeterRegistry meterRegistry) {
        return new BrokerResilienceMetrics(meterRegistry);
    }

    @Bean
    public CircuitBreakerMetrics circuitBreakerMetrics(BrokerResilienceMetrics brokerResilienceMetrics) {
        return brokerResilienceMetrics;
    }
}
