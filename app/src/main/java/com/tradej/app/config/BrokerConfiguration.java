package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
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
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.marketdata.LivePnlService;
import com.tradej.execution.marketdata.MarketDepthOrchestrator;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.options.service.OptionStrikeResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures Dhan broker connection using {@link BrokerComposition} for adapter wiring,
 * then exposes individual beans with observable wrappers for metrics.
 *
 * <p>Active by default. Deactivated when {@code trade.broker-type=upstox}.
 */
@Configuration
@ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
public class BrokerConfiguration {

    @Bean
    @Primary
    MultiBucketRateLimiter multiBucketRateLimiter() {
        return DhanProtocolConstants.defaultRateLimiter();
    }

    @Bean
    CaffeineIdempotencyCache idempotencyCache() {
        return new CaffeineIdempotencyCache();
    }

    @Bean
    @Primary
    OrderIdentityRegistry orderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    @Bean
    DhanConnectionSettings dhanConnectionSettings(TradingProperties properties) {
        TradingProperties.DhanProperties broker = properties.broker();
        return new DhanConnectionSettings(
                broker.clientId(),
                broker.accessToken(),
                broker.environment(),
                broker.restBaseUrl(),
                broker.loggingEnabled(),
                broker.rateLimitRetries(),
                broker.maxReconnectAttempts(),
                broker.autoReconnectEnabled(),
                broker.autoResubscribeEnabled(),
                broker.authMode(),
                DhanConfigPaths.resolve(broker.pinFile()),
                DhanConfigPaths.resolve(broker.totpSecretFile()),
                DhanConfigPaths.resolve(broker.tokenStateFile()),
                broker.refreshBufferMinutes(),
                null,
                false
        );
    }

    @Bean
    DhanTokenProvider dhanTokenProvider(DhanConnectionSettings settings) {
        return new DhanTokenManager(settings);
    }

    @Bean
    BrokerComposition brokerComposition(TradingProperties properties, CaffeineIdempotencyCache idempotencyCache) {
        TradingProperties.DhanProperties broker = properties.broker();
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        BrokerProfile.DhanConfig dhanConfig = new BrokerProfile.DhanConfig(
                broker.clientId(),
                broker.accessToken(),
                broker.environment(),
                broker.restBaseUrl(),
                broker.authMode(),
                DhanConfigPaths.resolve(broker.pinFile()),
                DhanConfigPaths.resolve(broker.totpSecretFile()),
                DhanConfigPaths.resolve(broker.tokenStateFile()),
                broker.refreshBufferMinutes(),
                instruments != null && instruments.autoDownload(),
                instruments != null ? instruments.cacheDirectory() : null
        );
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.DHAN, dhanConfig, null, null);
        return BrokerComposition.create(profile, idempotencyCache);
    }

    @Bean
    DhanBrokerConnection dhanBrokerConnection(BrokerComposition composition) {
        return (DhanBrokerConnection) composition.brokerConnection();
    }

    @Bean(name = {"brokerConnection", "dhanBrokerConnection"})
    IBrokerConnection brokerConnectionBean(DhanBrokerConnection dhanBrokerConnection) {
        return dhanBrokerConnection;
    }

    @Bean
    @Primary
    MarketDataProvider marketDataProvider(DhanBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableMarketDataProvider("dhan", conn.marketData(), meterRegistry);
    }

    @Bean
    @Primary
    OrderCommand orderCommand(DhanBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableOrderCommand("dhan", conn.orders(), meterRegistry);
    }

    @Bean
    InstrumentResolver instrumentResolver(DhanBrokerConnection conn) {
        return conn.instruments();
    }

    @Bean
    OrderQuery orderQuery(DhanBrokerConnection conn) {
        return conn.orderQuery();
    }

    @Bean
    PortfolioProvider portfolioProvider(DhanBrokerConnection conn) {
        return conn.portfolio();
    }

    @Bean
    MarginProvider marginProvider(DhanBrokerConnection conn) {
        return conn.margin();
    }

    @Bean
    FuturesProvider futuresProvider(DhanBrokerConnection conn) {
        return conn.futures();
    }

    @Bean
    OptionsProvider optionsProvider(DhanBrokerConnection conn) {
        return conn.options();
    }

    @Bean
    WebSocketMultiplexer webSocketMultiplexer(DhanBrokerConnection conn) {
        return conn.websocket();
    }

    @Bean
    SliceOrderCommand sliceOrderCommand(DhanBrokerConnection conn) {
        return conn.sliceOrders();
    }

    @Bean
    BracketOrderProvider bracketOrderProvider(DhanBrokerConnection conn) {
        return conn.bracketOrders();
    }

    @Bean
    GttOrderProvider gttOrderProvider(DhanBrokerConnection conn) {
        return conn.gttOrders();
    }

    @Bean
    SessionRiskProvider sessionRiskProvider(DhanBrokerConnection conn) {
        return conn.sessionRisk();
    }

    @Bean
    ConditionalAlertProvider conditionalAlertProvider(DhanBrokerConnection conn) {
        return conn.alerts();
    }

    @Bean
    BrokerCapabilities brokerCapabilities(TradingProperties properties) {
        return PropertiesBrokerCapabilities.from(properties.venues());
    }

    @Bean
    BrokerTransportCapabilities dhanTransportCapabilities() {
        return BrokerTransportCapabilities.dhanLive();
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
