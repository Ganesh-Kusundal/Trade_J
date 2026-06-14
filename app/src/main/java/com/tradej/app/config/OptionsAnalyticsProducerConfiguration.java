package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.port.EventBus;
import com.tradej.options.producer.OptionsAnalyticsProducer;
import com.tradej.options.surface.VolatilitySurfaceBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * Spring wiring + lifecycle for the Spring-free
 * {@link OptionsAnalyticsProducer} defined in the
 * {@code trading-options-analytics} module.
 */
@Configuration
@ConditionalOnProperty(name = "trade.options.analytics-enabled", havingValue = "true")
public class OptionsAnalyticsProducerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OptionsAnalyticsProducerConfiguration.class);

    private final ObjectProvider<IBrokerConnection> brokerConnection;
    private final EventBus eventBus;
    private final EventMetadataFactory metadataFactory;
    private final ObjectProvider<VolatilitySurfaceBuilder> surfaceBuilder;
    private final List<String> underlyings;
    private final long intervalMs;
    private final OptionsAnalyticsProducer producer;

    public OptionsAnalyticsProducerConfiguration(
            ObjectProvider<IBrokerConnection> brokerConnection,
            EventBus eventBus,
            EventMetadataFactory metadataFactory,
            ObjectProvider<VolatilitySurfaceBuilder> surfaceBuilder,
            @Lazy List<String> underlyings,
            @org.springframework.beans.factory.annotation.Value("${trade.options.chain-poll-interval-ms:60000}") long intervalMs
    ) {
        this.brokerConnection = brokerConnection;
        this.eventBus = eventBus;
        this.metadataFactory = metadataFactory;
        this.surfaceBuilder = surfaceBuilder;
        this.underlyings = underlyings == null ? List.of() : underlyings;
        this.intervalMs = intervalMs;
        IBrokerConnection conn = brokerConnection.getIfAvailable();
        if (conn == null) {
            this.producer = null;
        } else {
            this.producer = new OptionsAnalyticsProducer(
                    conn, eventBus, metadataFactory,
                    surfaceBuilder.getIfAvailable(),
                    this.underlyings, this.intervalMs
            );
        }
    }

    @PostConstruct
    void start() {
        if (producer == null) {
            log.warn("OptionsAnalyticsProducer not started: no broker connection");
            return;
        }
        producer.start();
    }

    @PreDestroy
    void stop() {
        if (producer != null) {
            producer.close();
        }
    }
}
