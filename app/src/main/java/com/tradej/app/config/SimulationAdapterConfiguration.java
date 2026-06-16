package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.brokergateway.config.BrokerProfile;
import com.tradej.brokergateway.wiring.BrokerComposition;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "simulation")
public class SimulationAdapterConfiguration {

    @Bean
    @Primary
    MultiBucketRateLimiter simulationRateLimiter() {
        return new MultiBucketRateLimiter(Map.of(
                "api", new com.tradej.broker.core.rate.RateLimitConfig("api", 100, 10),
                "order", new com.tradej.broker.core.rate.RateLimitConfig("order", 50, 5),
                "market-data", new com.tradej.broker.core.rate.RateLimitConfig("market-data", 200, 20)));
    }

    @Bean
    BrokerComposition simulationBrokerComposition() {
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.SIMULATION, null, null, null);
        return BrokerComposition.create(profile);
    }

    @Bean(name = "brokerConnection")
    @Primary
    IBrokerConnection brokerConnection(BrokerComposition composition) {
        return composition.brokerConnection();
    }

    @Bean
    @Primary
    MarketDataProvider marketDataProvider(IBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableMarketDataProvider("simulation", conn.marketData(), meterRegistry);
    }

    @Bean
    @Primary
    OrderCommand orderCommand(IBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableOrderCommand("simulation", conn.orders(), meterRegistry);
    }

    @Bean
    InstrumentResolver instrumentResolver(IBrokerConnection conn) {
        return conn.instruments();
    }

    @Bean
    OrderQuery orderQuery(IBrokerConnection conn) {
        return conn.orderQuery();
    }

    @Bean
    PortfolioProvider portfolioProvider(IBrokerConnection conn) {
        return conn.portfolio();
    }

    @Bean
    MarginProvider marginProvider(IBrokerConnection conn) {
        return conn.margin();
    }

    @Bean
    FuturesProvider futuresProvider(IBrokerConnection conn) {
        return conn.futures();
    }

    @Bean
    OptionsProvider optionsProvider(IBrokerConnection conn) {
        return conn.options();
    }

    @Bean
    WebSocketMultiplexer webSocketMultiplexer(IBrokerConnection conn) {
        return conn.websocket();
    }

    @Bean
    SliceOrderCommand sliceOrderCommand(IBrokerConnection conn) {
        return conn.sliceOrders();
    }

    @Bean
    BrokerTransportCapabilities simulationTransportCapabilities() {
        return new BrokerTransportCapabilities(true, true, true, true, false);
    }
}
