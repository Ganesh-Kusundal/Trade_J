package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
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
import com.tradej.broker.icici.config.BreezeConnectionSettings;
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
import com.tradej.composition.config.BrokerProfile;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;

import java.net.http.HttpClient;
import java.util.Map;

final class IciciBrokerFactory {

    private IciciBrokerFactory() {
    }

    static IBrokerConnection create(BrokerProfile.IciciConfig cfg) {
        BreezeConnectionSettings settings = BreezeConnectionSettings.withDefaults(
                cfg.appKey(),
                cfg.secretKey(),
                cfg.sessionToken(),
                cfg.authMode(),
                cfg.totpSecretFile(),
                cfg.usernameFile(),
                cfg.passwordFile(),
                cfg.apiSessionFile(),
                cfg.tokenStateFile(),
                cfg.ordersEnabled(),
                cfg.refreshBufferMinutes(),
                cfg.loginRedirectPort(),
                cfg.loginRedirectPath(),
                cfg.browserHeadless(),
                cfg.browserLoginTimeoutSeconds()
        );

        HttpClient httpClient = HttpClient.newHttpClient();
        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        BreezeTokenProvider tokenProvider = tokenManager;
        BreezeAuthenticatedHttpClient authenticatedHttpClient = new BreezeAuthenticatedHttpClient(tokenProvider);

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
        var portfolioProvider = new IciciPortfolioProvider(portfolioRestClient);
        var orderCommand = new IciciOrderCommandAdapter(orderRestClient, domainMapper, instrumentResolver, settings);
        var orderQuery = new IciciOrderQueryAdapter(orderRestClient, domainMapper);
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
}
