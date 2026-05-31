package com.tradej.cli.standalone;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
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
import com.tradej.broker.upstox.auth.UpstoxTokenManager;
import com.tradej.broker.upstox.config.UpstoxApiEnvironment;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.upstox.resilience.UpstoxResilienceExecutor;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxOptionChainRestClient;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.broker.upstox.rest.UpstoxPortfolioRestClient;
import com.tradej.broker.upstox.websocket.UpstoxFeedAuthorizer;
import com.tradej.broker.upstox.websocket.UpstoxStreamNormalizer;
import com.tradej.broker.upstox.websocket.UpstoxWebSocketMultiplexer;
import com.tradej.core.domain.event.EventMetadataFactory;

import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;

import java.net.http.HttpClient;
import java.util.Map;

public final class UpstoxCliConnectionFactory {
    private UpstoxCliConnectionFactory() {
    }

    public static UpstoxBrokerConnection create(UpstoxConnectionSettings settings) {
        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = settings.isSandbox()
                ? UpstoxApiEnvironment.SANDBOX.baseUrl()
                : UpstoxApiEnvironment.LIVE.baseUrl();

        UpstoxBearerTokenSource tokenSource = settings.analyticsOnly()
                ? new UpstoxAnalyticsTokenHolder(settings)
                : UpstoxTokenManager.create(
                        new UpstoxOAuthClient(httpClient, baseUrl),
                        settings,
                        DhanConfigPaths.resolve("runtime/upstox-token-state.json")
                );

        UpstoxHttpClient authenticatedClient = new UpstoxHttpClient(httpClient, tokenSource, baseUrl);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(authenticatedClient);

        MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                "DATA", new RateLimitConfig("DATA", 5.0, 3)
        ));
        UpstoxResilienceExecutor resilienceExecutor = new UpstoxResilienceExecutor(
                rateLimiter,
                new CircuitBreaker()
        );

        UpstoxInstrumentResolver instrumentResolver = new UpstoxInstrumentResolver();
        UpstoxInstrumentLoader instrumentLoader = new UpstoxInstrumentLoader(httpClient);
        UpstoxDomainMapper mapper = new UpstoxDomainMapper();
        EventMetadataFactory metadataFactory = new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock());

        MarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(jsonClient),
                instrumentResolver,
                new UpstoxHistoricalDataRestClient(jsonClient, resilienceExecutor)
        );

        OrderCommand orderCommand;
        OrderQuery orderQuery;
        PortfolioProvider portfolioProvider;
        MarginProvider marginProvider;
        if (settings.analyticsOnly()) {
            orderCommand = UpstoxUnsupportedPorts.ORDERS;
            orderQuery = UpstoxUnsupportedPorts.ORDER_QUERY;
            portfolioProvider = UpstoxUnsupportedPorts.PORTFOLIO;
            marginProvider = UpstoxUnsupportedPorts.MARGIN;
        } else {
            UpstoxOrderRestClient orderRestClient = new UpstoxOrderRestClient(jsonClient);
            orderCommand = new UpstoxOrderCommandAdapter(orderRestClient, mapper, instrumentResolver);
            orderQuery = new UpstoxOrderQueryAdapter(orderRestClient, mapper);
            portfolioProvider = new UpstoxPortfolioProvider(new UpstoxPortfolioRestClient(jsonClient));
            marginProvider = new UpstoxMarginProvider(jsonClient);
        }

        FuturesProvider futuresProvider = new UpstoxFuturesProvider(instrumentResolver);
        OptionsProvider optionsProvider = new UpstoxOptionsProvider(
                new UpstoxOptionChainRestClient(jsonClient),
                instrumentResolver
        );

        UpstoxWebSocketMultiplexer webSocketMultiplexer = new UpstoxWebSocketMultiplexer(
                new UpstoxFeedAuthorizer(authenticatedClient),
                new UpstoxStreamNormalizer(instrumentResolver, metadataFactory),
                instrumentResolver,
                metadataFactory
        );

        return new UpstoxBrokerConnection(
                marketData,
                orderCommand,
                orderQuery,
                portfolioProvider,
                marginProvider,
                instrumentResolver,
                webSocketMultiplexer,
                futuresProvider,
                optionsProvider,
                instrumentLoader
        );
    }
}
