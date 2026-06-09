package com.tradej.app.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.config.BrokerScopedEventBus;
import com.tradej.app.health.MarketDataHealthIndicator;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import java.util.function.Consumer;

@Configuration
public class EventBusConfiguration {

    @Lazy
    @Bean
    @Primary
    EventBus eventBus() {
        return new SimpleEventBus();
    }

    @Lazy
    @Bean("dhanEventBus")
    BrokerScopedEventBus dhanEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "dhan");
    }

    @Lazy
    @Bean("upstoxEventBus")
    BrokerScopedEventBus upstoxEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "upstox");
    }

    @Lazy
    @Bean("iciciEventBus")
    BrokerScopedEventBus iciciEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "icici");
    }

    @Lazy
    @Bean
    DisruptorBusMetrics disruptorBusMetrics(EventBus eventBus) {
        if (eventBus instanceof SimpleEventBus simple) {
            return new SimpleBusMetrics(simple);
        }
        return new NoOpBusMetrics();
    }

    @Lazy
    @Bean
    MarketDataPipeline marketDataPipeline(EventBus eventBus) {
        return new MarketDataPipeline((Consumer<DomainEvent>) eventBus::publish);
    }

    @Lazy
    @Bean
    OrderPipeline orderPipeline(EventBus eventBus) {
        return new OrderPipeline((Consumer<DomainEvent>) eventBus::publish);
    }

    @Bean
    MarketDataHealthIndicator marketDataHealthIndicator(
            @Lazy MarketDataPipeline marketDataPipeline,
            org.springframework.beans.factory.ObjectProvider<com.tradej.broker.api.model.BrokerTransportCapabilities> transportCapabilitiesProvider,
            com.tradej.app.health.AlertManager alertManager
    ) {
        return new MarketDataHealthIndicator(marketDataPipeline, transportCapabilitiesProvider, alertManager);
    }
}
