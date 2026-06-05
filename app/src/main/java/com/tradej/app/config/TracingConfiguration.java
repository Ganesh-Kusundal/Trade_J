package com.tradej.app.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Micrometer observations for hot-path tracing when {@code trade.tracing.enabled=true}.
 * Full OpenTelemetry agent can be attached via {@code -javaagent:opentelemetry-javaagent.jar}.
 */
@Configuration
@ConditionalOnProperty(name = "trade.tracing.enabled", havingValue = "true")
public class TracingConfiguration {

    @Bean
    ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}
