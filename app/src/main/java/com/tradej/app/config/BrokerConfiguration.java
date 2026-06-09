package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.composition.BrokerComposition;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.marketdata.LivePnlService;
import com.tradej.execution.marketdata.MarketDepthOrchestrator;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.options.service.OptionStrikeResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Broker-agnostic configuration that wires port beans from the active
 * {@link IBrokerConnection}. Dhan-specific beans (rate limiter, token provider,
 * connection settings) live in {@link DhanBrokerConfiguration}.
 *
 * @deprecated Migrated to {@link UnifiedBrokerConfiguration} to resolve competing
 * dependency graphs. This class will be removed in a future release.
 * The unified configuration creates all beans conditionally based on the
 * {@code trade.broker-type} property, ensuring a single dependency graph.
 */
@Deprecated(since = "2026-06-09", forRemoval = true)
@Configuration
public class BrokerConfiguration {

    @Bean
    CaffeineIdempotencyCache idempotencyCache() {
        return new CaffeineIdempotencyCache();
    }

    @Bean
    @Primary
    OrderIdentityRegistry orderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    @Bean(name = "brokerConnection")
    @Primary
    IBrokerConnection brokerConnection(BrokerComposition composition) {
        return composition.brokerConnection();
    }

    @Bean
    @Primary
    MarketDataProvider marketDataProvider(IBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableMarketDataProvider("active", conn.marketData(), meterRegistry);
    }

    @Bean
    @Primary
    OrderCommand orderCommand(IBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableOrderCommand("active", conn.orders(), meterRegistry);
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
    BracketOrderProvider bracketOrderProvider(IBrokerConnection conn) {
        return conn.bracketOrders();
    }

    @Bean
    GttOrderProvider gttOrderProvider(IBrokerConnection conn) {
        return conn.gttOrders();
    }

    @Bean
    SessionRiskProvider sessionRiskProvider(IBrokerConnection conn) {
        return conn.sessionRisk();
    }

    @Bean
    ConditionalAlertProvider conditionalAlertProvider(IBrokerConnection conn) {
        return conn.alerts();
    }

    @Bean
    BrokerCapabilities brokerCapabilities(TradingProperties properties) {
        return PropertiesBrokerCapabilities.from(properties.venues());
    }

    @Bean
    LivePnlService livePnlService(
            PortfolioProvider portfolioProvider,
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver
    ) {
        return new LivePnlService(portfolioProvider, marketDataProvider, instrumentResolver);
    }

    @Bean
    OptionStrikeResolver optionStrikeResolver(OptionsProvider optionsProvider) {
        return new OptionStrikeResolver(optionsProvider);
    }

    @Bean
    MarketDepthOrchestrator marketDepthOrchestrator(WebSocketMultiplexer webSocketMultiplexer) {
        return new MarketDepthOrchestrator(webSocketMultiplexer);
    }
}
