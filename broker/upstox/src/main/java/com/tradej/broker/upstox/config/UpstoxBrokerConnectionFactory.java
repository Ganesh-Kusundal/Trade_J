package com.tradej.broker.upstox.config;

import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.adapter.UpstoxDataServicesProvider;
import com.tradej.broker.upstox.adapter.UpstoxFuturesProvider;
import com.tradej.broker.upstox.adapter.UpstoxMarketDataProvider;
import com.tradej.broker.upstox.adapter.UpstoxMarginProvider;
import com.tradej.broker.upstox.adapter.UpstoxNewsProvider;
import com.tradej.broker.upstox.adapter.UpstoxOptionsProvider;
import com.tradej.broker.upstox.adapter.UpstoxOrderCommandAdapter;
import com.tradej.broker.upstox.adapter.UpstoxOrderQueryAdapter;
import com.tradej.broker.upstox.adapter.UpstoxPortfolioProvider;
import com.tradej.broker.upstox.adapter.UpstoxProfileProvider;
import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import com.tradej.broker.upstox.auth.UpstoxOAuthClient;
import com.tradej.broker.upstox.auth.UpstoxTokenManager;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.upstox.resilience.UpstoxRetryExecutor;
import com.tradej.broker.upstox.rest.UpstoxDataServicesRestClient;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxNewsRestClient;
import com.tradej.broker.upstox.rest.UpstoxOptionChainRestClient;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.broker.upstox.rest.UpstoxPortfolioRestClient;
import com.tradej.broker.upstox.rest.UpstoxProfileRestClient;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.broker.upstox.websocket.UpstoxFeedAuthorizer;
import com.tradej.broker.upstox.websocket.UpstoxStreamNormalizer;
import com.tradej.broker.upstox.websocket.UpstoxWebSocketMultiplexer;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Map;

/**
 * Factory that wires all internal Upstox adapters into an {@link UpstoxBrokerConnection}.
 *
 * <p>Extracted from the composition-layer factory to keep auto-configuration
 * self-contained within the broker/upstox module.
 */
final class UpstoxBrokerConnectionFactory {

    private UpstoxBrokerConnectionFactory() {
    }

    static UpstoxBrokerConnection create(UpstoxConnectionSettings settings) {
        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = settings.isSandbox()
                ? UpstoxApiEnvironment.SANDBOX.baseUrl()
                : UpstoxApiEnvironment.LIVE.baseUrl();

        UpstoxBearerTokenSource tokenSource = settings.analyticsOnly()
                ? new UpstoxAnalyticsTokenHolder(settings)
                : UpstoxTokenManager.create(
                        new UpstoxOAuthClient(httpClient, baseUrl),
                        settings,
                        Path.of("runtime/upstox-token-state.json")
                );

        UpstoxHttpClient authenticatedClient = new UpstoxHttpClient(httpClient, tokenSource, baseUrl);
        UpstoxJsonHttpClient jsonClient = new UpstoxJsonHttpClient(authenticatedClient);

        MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                "DATA", new RateLimitConfig("DATA", 5.0, 3)
        ));
        UpstoxRetryExecutor retryExecutor = new UpstoxRetryExecutor(
                rateLimiter,
                new CircuitBreaker()
        );

        UpstoxInstrumentResolver instrumentResolver = new UpstoxInstrumentResolver();
        UpstoxInstrumentLoader instrumentLoader = new UpstoxInstrumentLoader(httpClient);
        UpstoxDomainMapper mapper = new UpstoxDomainMapper();
        EventMetadataFactory metadataFactory = new EventMetadataFactory(new LiveTradingClock());

        MarketDataProvider marketData = new UpstoxMarketDataProvider(
                new UpstoxMarketDataRestClient(jsonClient),
                instrumentResolver,
                new UpstoxHistoricalDataRestClient(jsonClient, retryExecutor)
        );

        OrderCommand orderCommand;
        OrderQuery orderQuery;
        PortfolioProvider portfolioProvider;
        MarginProvider marginProvider;
        SliceOrderCommand sliceOrderCommand;
        if (settings.analyticsOnly()) {
            orderCommand = analyticsOnlyOrderCommand();
            orderQuery = analyticsOnlyOrderQuery();
            portfolioProvider = analyticsOnlyPortfolioProvider();
            marginProvider = (order) -> { throw new UnsupportedOperationException("Margin estimation not supported in analytics-only mode"); };
            sliceOrderCommand = null;
        } else {
            UpstoxOrderRestClient orderRestClient = new UpstoxOrderRestClient(jsonClient);
            UpstoxPortfolioRestClient portfolioRestClient = new UpstoxPortfolioRestClient(jsonClient);
            orderCommand = new UpstoxOrderCommandAdapter(orderRestClient, portfolioRestClient, mapper, instrumentResolver);
            orderQuery = new UpstoxOrderQueryAdapter(orderRestClient, mapper, instrumentResolver);
            portfolioProvider = new UpstoxPortfolioProvider(portfolioRestClient, instrumentResolver);
            marginProvider = new UpstoxMarginProvider(jsonClient);
            sliceOrderCommand = new com.tradej.broker.upstox.adapter.UpstoxSliceOrderAdapter(orderRestClient, mapper, instrumentResolver);
        }

        FuturesProvider futuresProvider = new UpstoxFuturesProvider(instrumentResolver);
        OptionsProvider optionsProvider = new UpstoxOptionsProvider(
                new UpstoxOptionChainRestClient(jsonClient),
                instrumentResolver
        );
        NewsProvider newsProvider = new UpstoxNewsProvider(
                new UpstoxNewsRestClient(jsonClient)
        );

        UpstoxWebSocketMultiplexer webSocketMultiplexer = new UpstoxWebSocketMultiplexer(
                new UpstoxFeedAuthorizer(authenticatedClient),
                new UpstoxStreamNormalizer(instrumentResolver, metadataFactory),
                instrumentResolver,
                metadataFactory
        );

        ConditionalAlertProvider conditionalAlertProvider = new com.tradej.broker.upstox.adapter.UpstoxGttOrderAdapter(
                new com.tradej.broker.upstox.rest.UpstoxGttRestClient(jsonClient),
                instrumentResolver
        );

        UpstoxDataServicesProvider dataServicesProvider = new UpstoxDataServicesProvider(
                new UpstoxDataServicesRestClient(jsonClient));
        UpstoxProfileProvider profileProvider = new UpstoxProfileProvider(
                new UpstoxProfileRestClient(jsonClient));

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
                newsProvider,
                conditionalAlertProvider,
                sliceOrderCommand,
                dataServicesProvider,
                profileProvider,
                instrumentLoader
        );
    }

    private static OrderCommand analyticsOnlyOrderCommand() {
        return new OrderCommand() {
            @Override public com.tradej.core.domain.model.Order placeOrder(com.tradej.core.domain.model.OrderRequest request) { throw new UnsupportedOperationException(); }
            @Override public com.tradej.core.domain.model.OrderPreview previewOrder(com.tradej.core.domain.model.OrderRequest request) { throw new UnsupportedOperationException(); }
            @Override public com.tradej.core.domain.model.Order modifyOrder(com.tradej.core.domain.model.ModifyOrderRequest request) { throw new UnsupportedOperationException(); }
            @Override public boolean cancelOrder(String orderId) { throw new UnsupportedOperationException(); }
            @Override public java.util.List<String> cancelAllOpenOrders() { throw new UnsupportedOperationException(); }
            @Override public java.util.List<String> cancelAndSquareOffIntradayPositions() { throw new UnsupportedOperationException(); }
            @Override public boolean setKillSwitch(boolean enabled) { throw new UnsupportedOperationException(); }
        };
    }

    private static OrderQuery analyticsOnlyOrderQuery() {
        return new OrderQuery() {
            @Override public com.tradej.core.domain.model.Order getOrder(String orderId) { throw new UnsupportedOperationException(); }
            @Override public java.util.List<com.tradej.core.domain.model.Order> getOrderBook() { throw new UnsupportedOperationException(); }
            @Override public java.util.List<com.tradej.core.domain.model.Trade> getTradeBook() { throw new UnsupportedOperationException(); }
            @Override public com.tradej.core.domain.value.OrderStatus getOrderStatus(String orderId) { throw new UnsupportedOperationException(); }
            @Override public java.util.OptionalLong getExecutedPricePaisa(String orderId) { throw new UnsupportedOperationException(); }
            @Override public java.util.OptionalLong getExchangeTimeMs(String orderId) { throw new UnsupportedOperationException(); }
        };
    }

    private static PortfolioProvider analyticsOnlyPortfolioProvider() {
        return new PortfolioProvider() {
            @Override public java.util.List<com.tradej.core.domain.model.Position> getPositions() { throw new UnsupportedOperationException(); }
            @Override public java.util.List<com.tradej.core.domain.model.Holding> getHoldings() { throw new UnsupportedOperationException(); }
            @Override public com.tradej.core.domain.model.Balance getBalance() { throw new UnsupportedOperationException(); }
        };
    }
}
