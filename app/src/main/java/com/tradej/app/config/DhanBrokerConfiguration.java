package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
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
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.brokergateway.wiring.BrokerComposition;
import com.tradej.brokergateway.config.BrokerProfile;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
public class DhanBrokerConfiguration {

    @Bean
    @Primary
    MultiBucketRateLimiter multiBucketRateLimiter() {
        return DhanProtocolConstants.defaultRateLimiter();
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
    BrokerTransportCapabilities dhanTransportCapabilities() {
        return BrokerTransportCapabilities.dhanLive();
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
}
