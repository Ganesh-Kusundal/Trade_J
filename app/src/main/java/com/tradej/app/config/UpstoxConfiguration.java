package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionMapper;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import com.tradej.broker.upstox.expired.BrokerExpiredOptionQueryService;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxExpiredInstrumentRestClient;
import com.tradej.broker.upstox.resilience.UpstoxRetryExecutor;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.execution.identity.OrderIdentityRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration for the Upstox broker adapter.
 *
 * @deprecated Migrated to {@link UnifiedBrokerConfiguration} to resolve competing
 * dependency graphs. This class will be removed in a future release.
 * All beans are now created conditionally based on {@code trade.broker-type} property.
 *
 * <p>Activated when {@code trade.broker-type=upstox}.
 */
@Deprecated(since = "2026-06-09", forRemoval = true)
@Configuration
@ConditionalOnExpression("'${trade.broker-type:}' == 'upstox'")
public class UpstoxConfiguration {

    @Bean
    UpstoxConnectionSettings upstoxConnectionSettings(TradingProperties properties) {
        TradingProperties.UpstoxProperties cfg = properties.upstox();
        if (cfg == null) {
            throw new IllegalStateException("trade.upstox configuration is required when broker-type=upstox");
        }
        return new UpstoxConnectionSettings(
                cfg.clientId(),
                cfg.clientSecret(),
                cfg.redirectUri(),
                cfg.accessToken(),
                cfg.refreshToken(),
                cfg.analyticsToken(),
                cfg.extendedToken(),
                cfg.analyticsOnly(),
                cfg.sandbox(),
                cfg.redirectServerPort(),
                cfg.refreshBufferMs(),
                cfg.tokenExpiryBufferMs()
        );
    }

    @Bean
    OrderIdentityRegistry orderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    @Bean
    BrokerComposition upstoxBrokerComposition(TradingProperties properties) {
        TradingProperties.UpstoxProperties cfg = properties.upstox();
        BrokerProfile.UpstoxConfig upstoxConfig = new BrokerProfile.UpstoxConfig(
                cfg.clientId(),
                cfg.clientSecret(),
                cfg.redirectUri(),
                cfg.accessToken(),
                cfg.refreshToken(),
                cfg.analyticsToken(),
                cfg.extendedToken(),
                cfg.analyticsOnly(),
                cfg.sandbox(),
                cfg.redirectServerPort(),
                cfg.refreshBufferMs(),
                cfg.tokenExpiryBufferMs()
        );
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.UPSTOX, null, upstoxConfig, null);
        return BrokerComposition.create(profile);
    }

    @Bean
    UpstoxBrokerConnection upstoxBrokerConnection(BrokerComposition upstoxBrokerComposition) {
        return (UpstoxBrokerConnection) upstoxBrokerComposition.brokerConnection();
    }

    @Bean(name = {"brokerConnection", "upstoxBrokerConnection"})
    IBrokerConnection brokerConnectionBean(UpstoxBrokerConnection conn) {
        return conn;
    }

    @Bean
    @Primary
    MarketDataProvider marketDataProvider(UpstoxBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableMarketDataProvider("upstox", conn.marketData(), meterRegistry);
    }

    @Bean
    @Primary
    OrderCommand orderCommand(UpstoxBrokerConnection conn, MeterRegistry meterRegistry) {
        return new ObservableOrderCommand("upstox", conn.orders(), meterRegistry);
    }

    @Bean
    InstrumentResolver instrumentResolver(UpstoxBrokerConnection conn) {
        return conn.instruments();
    }

    @Bean
    OrderQuery orderQuery(UpstoxBrokerConnection conn) {
        return conn.orderQuery();
    }

    @Bean
    PortfolioProvider portfolioProvider(UpstoxBrokerConnection conn) {
        return conn.portfolio();
    }

    @Bean
    MarginProvider marginProvider(UpstoxBrokerConnection conn) {
        return conn.margin();
    }

    @Bean
    FuturesProvider futuresProvider(UpstoxBrokerConnection conn) {
        return conn.futures();
    }

    @Bean
    OptionsProvider optionsProvider(UpstoxBrokerConnection conn) {
        return conn.options();
    }

    @Bean
    WebSocketMultiplexer webSocketMultiplexer(UpstoxBrokerConnection conn) {
        return conn.websocket();
    }

    @Bean
    SliceOrderCommand sliceOrderCommand(UpstoxBrokerConnection conn) {
        return conn.sliceOrders();
    }

    @Bean
    ConditionalAlertProvider conditionalAlertProvider(UpstoxBrokerConnection conn) {
        return conn.alerts();
    }

    @Bean
    NewsProvider newsProvider(UpstoxBrokerConnection conn) {
        return conn.news();
    }

    @Bean
    UpstoxExpiredOptionMapper upstoxExpiredOptionMapper() {
        return new UpstoxExpiredOptionMapper();
    }

    @Bean
    UpstoxExpiredInstrumentRestClient upstoxExpiredInstrumentRestClient(
            TradingProperties properties,
            UpstoxConnectionSettings settings
    ) {
        TradingProperties.UpstoxProperties cfg = properties.upstox();
        String algoToken = cfg.accessToken();
        if (algoToken == null || algoToken.isBlank()) {
            return null;
        }
        String baseUrl = settings.isSandbox()
                ? com.tradej.broker.upstox.config.UpstoxApiEnvironment.SANDBOX.baseUrl()
                : com.tradej.broker.upstox.config.UpstoxApiEnvironment.LIVE.baseUrl();
        var tokenSource = new com.tradej.broker.upstox.auth.UpstoxStaticTokenHolder(algoToken, false, "Expired instruments");
        var httpClient = new com.tradej.broker.upstox.http.UpstoxHttpClient(
                java.net.http.HttpClient.newHttpClient(), tokenSource, baseUrl);
        var jsonClient = new com.tradej.broker.upstox.http.UpstoxJsonHttpClient(httpClient);
        var retryExecutor = new UpstoxRetryExecutor(
                new com.tradej.broker.core.rate.MultiBucketRateLimiter(java.util.Map.of(
                        "EXPIRED_INSTRUMENT", new com.tradej.broker.core.rate.RateLimitConfig("EXPIRED_INSTRUMENT", 50.0, 50))),
                new com.tradej.broker.core.resilience.CircuitBreaker());
        return new UpstoxExpiredInstrumentRestClient(jsonClient, retryExecutor);
    }

    @Bean
    UpstoxExpiredOptionService upstoxExpiredOptionService(
            UpstoxExpiredInstrumentRestClient restClient,
            UpstoxExpiredOptionMapper mapper,
            UpstoxBrokerConnection conn
    ) {
        UpstoxInstrumentResolver resolver = (UpstoxInstrumentResolver) conn.instruments();
        return new UpstoxExpiredOptionService(restClient, mapper, resolver);
    }

    @Bean
    BrokerCapabilities upstoxBrokerCapabilities(TradingProperties properties) {
        return PropertiesBrokerCapabilities.from(properties.venues());
    }

    @Bean
    BrokerTransportCapabilities upstoxTransportCapabilities(UpstoxConnectionSettings settings) {
        return settings.analyticsOnly()
                ? BrokerTransportCapabilities.upstoxAnalytics()
                : BrokerTransportCapabilities.upstoxTrading();
    }
}
