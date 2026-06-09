package com.tradej.app.config;

import com.tradej.core.tracing.SpanFactory;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Micrometer observations for hot-path tracing when {@code trade.tracing.enabled=true}.
 * Registers a {@link MicrometerSpanAdapter} with {@link SpanFactory} so that
 * {@code SpanFactory.startSpan("broker.quote")} creates real Micrometer observations
 * exported to Prometheus and any attached OpenTelemetry agent.
 */
@Configuration
@ConditionalOnProperty(name = "trade.tracing.enabled", havingValue = "true")
public class TracingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TracingConfiguration.class);

    @Bean
    ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        SpanFactory.registerNamed(new MicrometerSpanAdapter(registry));
        log.info("Distributed tracing enabled — Micrometer observations registered with SpanFactory");
        return registry;
    }
}
