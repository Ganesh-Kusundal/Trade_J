package com.tradej.broker.icici.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.adapter.IciciFuturesProvider;
import com.tradej.broker.icici.adapter.IciciMarginProvider;
import com.tradej.broker.icici.adapter.IciciMarketDataProvider;
import com.tradej.broker.icici.adapter.IciciOptionsProvider;
import com.tradej.broker.icici.adapter.IciciOrderCommandAdapter;
import com.tradej.broker.icici.adapter.IciciOrderQueryAdapter;
import com.tradej.broker.icici.adapter.IciciPortfolioProvider;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.instrument.BreezeInstrumentLoader;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.historical.BreezeHistoricalDataService;
import com.tradej.broker.icici.resilience.IciciResilienceExecutor;
import com.tradej.broker.icici.rest.BreezeHistoricalRestClient;
import com.tradej.broker.icici.rest.BreezeMarketDataRestClient;
import com.tradej.broker.icici.rest.BreezeOptionChainRestClient;
import com.tradej.broker.icici.rest.BreezeOrderRestClient;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import com.tradej.broker.icici.websocket.BreezeWebSocketMultiplexer;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(IciciBrokerConnection.class)
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "icici")
public class IciciAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BreezeConnectionSettings breezeConnectionSettings(
            org.springframework.core.env.Environment environment) {
        return BreezeConnectionSettings.withDefaults(
                environment.getRequiredProperty("trade.icici.app-key"),
                environment.getRequiredProperty("trade.icici.secret-key"),
                environment.getProperty("trade.icici.session-token"),
                IciciAuthMode.valueOf(environment.getProperty("trade.icici.auth-mode", "BROWSER_AUTOMATED")),
                getPathOrNull(environment, "trade.icici.totp-secret-file"),
                getPathOrNull(environment, "trade.icici.username-file"),
                getPathOrNull(environment, "trade.icici.password-file"),
                getPathOrNull(environment, "trade.icici.api-session-file"),
                getPathOrNull(environment, "trade.icici.token-state-file"),
                environment.getProperty("trade.icici.orders-enabled", Boolean.class, false),
                environment.getProperty("trade.icici.refresh-buffer-minutes", Long.class, 10L),
                environment.getProperty("trade.icici.login-redirect-port", Integer.class, 9080),
                environment.getProperty("trade.icici.login-redirect-path", "/api"),
                environment.getProperty("trade.icici.browser-headless", Boolean.class, true),
                environment.getProperty("trade.icici.browser-login-timeout-seconds", Long.class, 120L)
        );
    }

    @Bean
    @ConditionalOnMissingBean(BreezeTokenProvider.class)
    public BreezeTokenManager breezeTokenManager(BreezeConnectionSettings settings) {
        return new BreezeTokenManager(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public BreezeAuthenticatedHttpClient breezeAuthenticatedHttpClient(BreezeTokenProvider tokenProvider) {
        return new BreezeAuthenticatedHttpClient(tokenProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public BrokerLifecycleManager brokerLifecycleManager() {
        return new BrokerLifecycleManager();
    }

    @Bean
    @ConditionalOnMissingBean(IBrokerConnection.class)
    public IciciBrokerConnection iciciBrokerConnection(
            BreezeConnectionSettings settings,
            BreezeTokenProvider tokenProvider,
            BreezeAuthenticatedHttpClient authenticatedHttpClient) {

        HttpClient httpClient = HttpClient.newHttpClient();

        MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                IciciResilienceExecutor.CATEGORY_DATA, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DATA, 100.0 / 60.0, 100),
                IciciResilienceExecutor.CATEGORY_DAILY, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DAILY, 5000.0 / 86400.0, 5000),
                "ORDER", new RateLimitConfig("ORDER", 10.0, 10)
        ));
        IciciResilienceExecutor resilienceExecutor = new IciciResilienceExecutor(rateLimiter);
        BreezeDomainMapper domainMapper = new BreezeDomainMapper();
        BreezeInstrumentLoader instrumentLoader = new BreezeInstrumentLoader(httpClient);
        BreezeInstrumentResolver instrumentResolver = new BreezeInstrumentResolver(instrumentLoader);

        BreezeMarketDataRestClient marketDataRestClient = new BreezeMarketDataRestClient(authenticatedHttpClient);
        BreezeHistoricalRestClient historicalRestClient = new BreezeHistoricalRestClient(authenticatedHttpClient);
        BreezePortfolioRestClient portfolioRestClient = new BreezePortfolioRestClient(authenticatedHttpClient);
        BreezeOrderRestClient orderRestClient = new BreezeOrderRestClient(authenticatedHttpClient);
        BreezeOptionChainRestClient optionChainRestClient = new BreezeOptionChainRestClient(authenticatedHttpClient);

        BreezeHistoricalDataService historicalDataService = new BreezeHistoricalDataService(
                historicalRestClient, domainMapper, resilienceExecutor);

        var marketDataProvider = new IciciMarketDataProvider(
                marketDataRestClient, historicalDataService, instrumentResolver, domainMapper);
        var portfolioProvider = new IciciPortfolioProvider(portfolioRestClient, instrumentResolver);
        var orderCommand = new IciciOrderCommandAdapter(orderRestClient, domainMapper, instrumentResolver, settings);
        var orderQuery = new IciciOrderQueryAdapter(orderRestClient, domainMapper, instrumentResolver);
        var optionsProvider = new IciciOptionsProvider(optionChainRestClient, instrumentResolver, domainMapper);
        var futuresProvider = new IciciFuturesProvider(instrumentResolver);
        var marginProvider = new IciciMarginProvider(authenticatedHttpClient, instrumentResolver, domainMapper);

        EventMetadataFactory metadataFactory = new EventMetadataFactory(new LiveTradingClock());
        ReconnectListenerRegistry reconnectRegistry = new ReconnectListenerRegistry();
        var webSocketMultiplexer = new BreezeWebSocketMultiplexer(
                tokenProvider, instrumentResolver, metadataFactory, reconnectRegistry);

        return new IciciBrokerConnection(
                marketDataProvider,
                futuresProvider,
                optionsProvider,
                orderCommand,
                orderQuery,
                portfolioProvider,
                marginProvider,
                instrumentResolver,
                webSocketMultiplexer
        );
    }

    private static java.nio.file.Path getPathOrNull(
            org.springframework.core.env.Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return java.nio.file.Path.of(value);
    }
}
