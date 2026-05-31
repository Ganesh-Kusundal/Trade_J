package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionMapper;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import com.tradej.broker.upstox.resilience.UpstoxResilienceExecutor;
import com.tradej.broker.upstox.rest.UpstoxExpiredInstrumentRestClient;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.adapter.UpstoxFuturesProvider;
import com.tradej.broker.upstox.adapter.UpstoxMarketDataProvider;
import com.tradej.broker.upstox.adapter.UpstoxMarginProvider;
import com.tradej.broker.upstox.adapter.UpstoxOptionsProvider;
import com.tradej.broker.upstox.adapter.UpstoxOrderCommandAdapter;
import com.tradej.broker.upstox.adapter.UpstoxOrderQueryAdapter;
import com.tradej.broker.upstox.adapter.UpstoxPortfolioProvider;
import com.tradej.broker.upstox.adapter.UpstoxUnsupportedPorts;
import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import com.tradej.broker.upstox.auth.UpstoxOAuthClient;
import com.tradej.broker.upstox.auth.UpstoxStaticTokenHolder;
import com.tradej.broker.upstox.auth.UpstoxTokenManager;
import com.tradej.broker.upstox.config.UpstoxApiEnvironment;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxOptionChainRestClient;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.broker.upstox.rest.UpstoxPortfolioRestClient;
import com.tradej.broker.upstox.websocket.UpstoxFeedAuthorizer;
import com.tradej.broker.upstox.websocket.UpstoxStreamNormalizer;
import com.tradej.broker.upstox.websocket.UpstoxWebSocketMultiplexer;
import com.tradej.core.domain.event.EventMetadataFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Map;

/**
 * Spring configuration for the Upstox broker adapter.
 * Activated when {@code trade.broker-type=upstox}.
 */
@Configuration
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "upstox")
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
                cfg.analyticsOnly(),
                cfg.sandbox(),
                cfg.redirectServerPort(),
                cfg.refreshBufferMs(),
                cfg.tokenExpiryBufferMs()
        );
    }

    @Bean
    HttpClient upstoxJavaHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    UpstoxOAuthClient upstoxOAuthClient(HttpClient upstoxJavaHttpClient, UpstoxConnectionSettings settings) {
        String baseUrl = settings.isSandbox()
                ? UpstoxApiEnvironment.SANDBOX.baseUrl()
                : UpstoxApiEnvironment.LIVE.baseUrl();
        return new UpstoxOAuthClient(upstoxJavaHttpClient, baseUrl);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "false", matchIfMissing = true)
    UpstoxTokenManager upstoxTokenManager(
            UpstoxOAuthClient oauthClient,
            UpstoxConnectionSettings settings
    ) {
        Path tokenPath = Path.of("runtime/upstox-token-state.json");
        return UpstoxTokenManager.create(oauthClient, settings, tokenPath);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "true")
    UpstoxAnalyticsTokenHolder upstoxAnalyticsTokenHolder(UpstoxConnectionSettings settings) {
        return new UpstoxAnalyticsTokenHolder(settings);
    }

    @Bean
    UpstoxHttpClient upstoxAuthenticatedHttpClient(
            HttpClient upstoxJavaHttpClient,
            UpstoxBearerTokenSource tokenSource,
            UpstoxConnectionSettings settings
    ) {
        String baseUrl = settings.isSandbox()
                ? UpstoxApiEnvironment.SANDBOX.baseUrl()
                : UpstoxApiEnvironment.LIVE.baseUrl();
        return new UpstoxHttpClient(upstoxJavaHttpClient, tokenSource, baseUrl);
    }

    /**
     * Plus expired-instruments APIs require the algo/access token, not the read-only analytics JWT.
     */
    @Bean
    UpstoxBearerTokenSource upstoxExpiredInstrumentTokenSource(
            UpstoxConnectionSettings settings,
            ObjectProvider<UpstoxTokenManager> tokenManagerProvider
    ) {
        if (settings.accessToken() != null && !settings.accessToken().isBlank()) {
            return new UpstoxStaticTokenHolder(
                    settings.accessToken(),
                    false,
                    "Upstox access token (Plus expired instruments)");
        }
        UpstoxTokenManager tokenManager = tokenManagerProvider.getIfAvailable();
        if (tokenManager != null) {
            return tokenManager;
        }
        throw new IllegalStateException(
                "upstox.live.accessToken (Plus algo token) is required for expired instruments; "
                        + "read-only analytics token cannot call /expired-instruments/*");
    }

    @Bean
    UpstoxHttpClient upstoxExpiredInstrumentHttpClient(
            HttpClient upstoxJavaHttpClient,
            UpstoxBearerTokenSource upstoxExpiredInstrumentTokenSource,
            UpstoxConnectionSettings settings
    ) {
        String baseUrl = settings.isSandbox()
                ? UpstoxApiEnvironment.SANDBOX.baseUrl()
                : UpstoxApiEnvironment.LIVE.baseUrl();
        return new UpstoxHttpClient(upstoxJavaHttpClient, upstoxExpiredInstrumentTokenSource, baseUrl);
    }

    @Bean
    UpstoxJsonHttpClient upstoxExpiredInstrumentJsonHttpClient(
            UpstoxHttpClient upstoxExpiredInstrumentHttpClient
    ) {
        return new UpstoxJsonHttpClient(upstoxExpiredInstrumentHttpClient);
    }

    @Bean
    com.tradej.execution.identity.OrderIdentityRegistry orderIdentityRegistry() {
        return new com.tradej.execution.identity.OrderIdentityRegistry();
    }

    @Bean
    MultiBucketRateLimiter upstoxRateLimiter() {
        return new MultiBucketRateLimiter(Map.of(
                "ORDER", new RateLimitConfig("ORDER", 10.0, 10),
                "DATA", new RateLimitConfig("DATA", 5.0, 3),
                "QUOTE", new RateLimitConfig("QUOTE", 5.0, 3),
                "OPTION_CHAIN", new RateLimitConfig("OPTION_CHAIN", 3.0, 2),
                "EXPIRED_INSTRUMENT", new RateLimitConfig("EXPIRED_INSTRUMENT", 3.0, 2),
                "NON_TRADING", new RateLimitConfig("NON_TRADING", 10.0, 15)
        ));
    }

    @Bean
    CircuitBreaker upstoxCircuitBreaker() {
        return new CircuitBreaker();
    }

    @Bean
    UpstoxResilienceExecutor upstoxResilienceExecutor(
            MultiBucketRateLimiter upstoxRateLimiter,
            CircuitBreaker upstoxCircuitBreaker
    ) {
        return new UpstoxResilienceExecutor(upstoxRateLimiter, upstoxCircuitBreaker);
    }

    @Bean
    UpstoxJsonHttpClient upstoxJsonHttpClient(UpstoxHttpClient upstoxAuthenticatedHttpClient) {
        return new UpstoxJsonHttpClient(upstoxAuthenticatedHttpClient);
    }

    @Bean
    UpstoxMarketDataRestClient upstoxMarketDataRestClient(UpstoxJsonHttpClient upstoxJsonHttpClient) {
        return new UpstoxMarketDataRestClient(upstoxJsonHttpClient);
    }

    @Bean
    UpstoxHistoricalDataRestClient upstoxHistoricalDataRestClient(
            UpstoxJsonHttpClient upstoxJsonHttpClient,
            UpstoxResilienceExecutor upstoxResilienceExecutor
    ) {
        return new UpstoxHistoricalDataRestClient(upstoxJsonHttpClient, upstoxResilienceExecutor);
    }

    @Bean
    UpstoxOrderRestClient upstoxOrderRestClient(UpstoxJsonHttpClient upstoxJsonHttpClient) {
        return new UpstoxOrderRestClient(upstoxJsonHttpClient);
    }

    @Bean
    UpstoxPortfolioRestClient upstoxPortfolioRestClient(UpstoxJsonHttpClient upstoxJsonHttpClient) {
        return new UpstoxPortfolioRestClient(upstoxJsonHttpClient);
    }

    @Bean
    UpstoxOptionChainRestClient upstoxOptionChainRestClient(UpstoxJsonHttpClient upstoxJsonHttpClient) {
        return new UpstoxOptionChainRestClient(upstoxJsonHttpClient);
    }

    @Bean
    UpstoxExpiredInstrumentRestClient upstoxExpiredInstrumentRestClient(
            UpstoxJsonHttpClient upstoxExpiredInstrumentJsonHttpClient,
            UpstoxResilienceExecutor upstoxResilienceExecutor
    ) {
        return new UpstoxExpiredInstrumentRestClient(
                upstoxExpiredInstrumentJsonHttpClient,
                upstoxResilienceExecutor);
    }

    @Bean
    UpstoxExpiredOptionMapper upstoxExpiredOptionMapper() {
        return new UpstoxExpiredOptionMapper();
    }

    @Bean
    UpstoxExpiredOptionService upstoxExpiredOptionService(
            UpstoxExpiredInstrumentRestClient upstoxExpiredInstrumentRestClient,
            UpstoxExpiredOptionMapper upstoxExpiredOptionMapper,
            UpstoxInstrumentResolver upstoxInstrumentResolver
    ) {
        return new UpstoxExpiredOptionService(
                upstoxExpiredInstrumentRestClient,
                upstoxExpiredOptionMapper,
                upstoxInstrumentResolver
        );
    }

    @Bean
    UpstoxDomainMapper upstoxDomainMapper() {
        return new UpstoxDomainMapper();
    }

    @Bean
    UpstoxInstrumentResolver upstoxInstrumentResolver() {
        return new UpstoxInstrumentResolver();
    }

    @Bean
    UpstoxInstrumentLoader upstoxInstrumentLoader(HttpClient upstoxJavaHttpClient) {
        return new UpstoxInstrumentLoader(upstoxJavaHttpClient);
    }

    @Bean
    UpstoxFeedAuthorizer upstoxFeedAuthorizer(UpstoxHttpClient httpClient) {
        return new UpstoxFeedAuthorizer(httpClient);
    }

    @Bean
    UpstoxStreamNormalizer upstoxStreamNormalizer(
            UpstoxInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory
    ) {
        return new UpstoxStreamNormalizer(instrumentResolver, metadataFactory);
    }

    @Bean
    UpstoxWebSocketMultiplexer upstoxWebSocketMultiplexer(
            UpstoxFeedAuthorizer feedAuthorizer,
            UpstoxStreamNormalizer streamNormalizer,
            UpstoxInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory
    ) {
        return new UpstoxWebSocketMultiplexer(
                feedAuthorizer, streamNormalizer, instrumentResolver, metadataFactory);
    }

    // ─── Domain adapters ─────────────────────────────────────────────────

    @Bean
    com.tradej.broker.api.port.MarketDataProvider upstoxMarketDataProvider(
            UpstoxMarketDataRestClient restClient,
            UpstoxHistoricalDataRestClient historicalRestClient,
            UpstoxInstrumentResolver instrumentResolver,
            MeterRegistry meterRegistry
    ) {
        var delegate = new UpstoxMarketDataProvider(restClient, instrumentResolver, historicalRestClient);
        return new ObservableMarketDataProvider("upstox", delegate, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "false", matchIfMissing = true)
    com.tradej.broker.api.port.OrderCommand upstoxOrderCommand(
            UpstoxOrderRestClient restClient,
            UpstoxDomainMapper mapper,
            UpstoxInstrumentResolver instrumentResolver,
            MeterRegistry meterRegistry
    ) {
        var delegate = new UpstoxOrderCommandAdapter(restClient, mapper, instrumentResolver);
        return new ObservableOrderCommand("upstox", delegate, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "true")
    com.tradej.broker.api.port.OrderCommand upstoxAnalyticsOrderCommand() {
        return UpstoxUnsupportedPorts.ORDERS;
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "false", matchIfMissing = true)
    com.tradej.broker.api.port.OrderQuery upstoxOrderQuery(
            UpstoxOrderRestClient restClient,
            UpstoxDomainMapper mapper
    ) {
        return new UpstoxOrderQueryAdapter(restClient, mapper);
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "true")
    com.tradej.broker.api.port.OrderQuery upstoxAnalyticsOrderQuery() {
        return UpstoxUnsupportedPorts.ORDER_QUERY;
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "false", matchIfMissing = true)
    com.tradej.broker.api.port.PortfolioProvider upstoxPortfolioProvider(
            UpstoxPortfolioRestClient restClient
    ) {
        return new UpstoxPortfolioProvider(restClient);
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "true")
    com.tradej.broker.api.port.PortfolioProvider upstoxAnalyticsPortfolioProvider() {
        return UpstoxUnsupportedPorts.PORTFOLIO;
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "false", matchIfMissing = true)
    com.tradej.broker.api.port.MarginProvider upstoxMarginProvider(
            UpstoxJsonHttpClient upstoxJsonHttpClient
    ) {
        return new UpstoxMarginProvider(upstoxJsonHttpClient);
    }

    @Bean
    @ConditionalOnProperty(name = "trade.upstox.analytics-only", havingValue = "true")
    com.tradej.broker.api.port.MarginProvider upstoxAnalyticsMarginProvider() {
        return UpstoxUnsupportedPorts.MARGIN;
    }

    @Bean
    com.tradej.broker.api.port.FuturesProvider upstoxFuturesProvider(
            UpstoxInstrumentResolver instrumentResolver
    ) {
        return new UpstoxFuturesProvider(instrumentResolver);
    }

    @Bean
    com.tradej.broker.api.port.OptionsProvider upstoxOptionsProvider(
            UpstoxOptionChainRestClient restClient,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        return new UpstoxOptionsProvider(restClient, instrumentResolver);
    }

    @Bean
    IBrokerConnection upstoxBrokerConnection(
            com.tradej.broker.api.port.MarketDataProvider marketDataProvider,
            com.tradej.broker.api.port.OrderCommand orderCommand,
            com.tradej.broker.api.port.OrderQuery orderQuery,
            com.tradej.broker.api.port.PortfolioProvider portfolioProvider,
            com.tradej.broker.api.port.MarginProvider marginProvider,
            UpstoxInstrumentResolver instrumentResolver,
            UpstoxWebSocketMultiplexer webSocketMultiplexer,
            com.tradej.broker.api.port.FuturesProvider futuresProvider,
            com.tradej.broker.api.port.OptionsProvider optionsProvider,
            UpstoxInstrumentLoader instrumentLoader
    ) {
        return new UpstoxBrokerConnection(
                marketDataProvider, orderCommand, orderQuery,
                portfolioProvider, marginProvider, instrumentResolver,
                webSocketMultiplexer, futuresProvider, optionsProvider,
                instrumentLoader
        );
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
