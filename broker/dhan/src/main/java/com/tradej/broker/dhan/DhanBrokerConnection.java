package com.tradej.broker.dhan;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.AdvancedOrderCapable;
import com.tradej.broker.api.capability.AlertCapable;
import com.tradej.broker.api.capability.FuturesCapable;
import com.tradej.broker.api.capability.MarginCapable;
import com.tradej.broker.api.capability.OptionsCapable;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.adapter.DhanMarketDataProvider;
import com.tradej.broker.dhan.adapter.DhanOrderCommandAdapter;
import com.tradej.broker.dhan.adapter.DhanOrderQueryAdapter;
import com.tradej.broker.dhan.adapter.DhanPortfolioProvider;
import com.tradej.broker.dhan.adapter.InMemoryInstrumentResolver;
import com.tradej.broker.dhan.adapter.DhanFuturesAdapter;
import com.tradej.broker.dhan.adapter.DhanBracketOrderAdapter;
import com.tradej.broker.dhan.adapter.DhanConditionalAlertProvider;
import com.tradej.broker.dhan.adapter.DhanGttOrderAdapter;
import com.tradej.broker.dhan.adapter.DhanMarginProvider;
import com.tradej.broker.dhan.adapter.DhanSessionRiskProvider;
import com.tradej.broker.dhan.adapter.DhanSliceOrderAdapter;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanBrokerStartup;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.broker.dhan.historical.DhanHistoricalDataMapper;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.adapter.DhanOptionsAdapter;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.options.DhanOptionChainClient;
import com.tradej.broker.dhan.options.DhanRollingOptionClient;
import com.tradej.broker.dhan.options.OptionExpiryCache;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.broker.dhan.websocket.DhanWebSocketMultiplexer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class DhanBrokerConnection implements IBrokerConnection {

    private static final OptionsCapable OPTIONS_CAPABLE = new OptionsCapable() {};
    private static final FuturesCapable FUTURES_CAPABLE = new FuturesCapable() {};
    private static final MarginCapable MARGIN_CAPABLE = new MarginCapable() {};
    private static final AlertCapable ALERT_CAPABLE = new AlertCapable() {};
    private static final AdvancedOrderCapable ADVANCED_ORDER_CAPABLE = new AdvancedOrderCapable() {};

    private final DhanClientHolder clientHolder;
    private final DhanInstrumentResolver instrumentResolver;
    private final MarketDataProvider marketDataProvider;
    private final FuturesProvider futuresProvider;
    private final OptionsProvider optionsProvider;
    private final OrderCommand orderCommand;
    private final OrderQuery orderQuery;
    private final SliceOrderCommand sliceOrderCommand;
    private final BracketOrderProvider bracketOrderProvider;
    private final GttOrderProvider gttOrderProvider;
    private final PortfolioProvider portfolioProvider;
    private final MarginProvider marginProvider;
    private final SessionRiskProvider sessionRiskProvider;
    private final ConditionalAlertProvider conditionalAlertProvider;
    private final WebSocketMultiplexer webSocketMultiplexer;

    /**
     * Convenience factory that auto-creates a default {@link com.tradej.broker.core.rate.MultiBucketRateLimiter}.
     *
     * @deprecated For Spring-managed environments, use the
     *             {@link #DhanBrokerConnection(DhanClientHolder, DhanInstrumentResolver, MarketDataProvider, FuturesProvider, OptionsProvider, OrderCommand, OrderQuery, PortfolioProvider, WebSocketMultiplexer)}
     *             multi-adapter constructor instead. This factory exists only for legacy
     *             non-DI callers.
     */
    @Deprecated
    public static DhanBrokerConnection create(
            DhanConnectionSettings settings,
            IdempotencyCachePort idempotencyCache
    ) {
        return new DhanBrokerConnection(settings, DhanProtocolConstants.defaultRateLimiter(), idempotencyCache);
    }

    /**
     * Multi-adapter constructor — all dependencies are injected from outside.
     * This is the primary constructor for DI environments (Spring, etc.).
     *
     * <p>Each adapter is already fully constructed with its own dependencies.
     * {@code DhanBrokerConnection} is reduced to a pure facade that exposes
     * the adapters via the {@link IBrokerConnection} interface methods.
     */
    public DhanBrokerConnection(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            MarketDataProvider marketDataProvider,
            FuturesProvider futuresProvider,
            OptionsProvider optionsProvider,
            OrderCommand orderCommand,
            OrderQuery orderQuery,
            SliceOrderCommand sliceOrderCommand,
            BracketOrderProvider bracketOrderProvider,
            GttOrderProvider gttOrderProvider,
            PortfolioProvider portfolioProvider,
            MarginProvider marginProvider,
            SessionRiskProvider sessionRiskProvider,
            ConditionalAlertProvider conditionalAlertProvider,
            WebSocketMultiplexer webSocketMultiplexer
    ) {
        this.clientHolder = Objects.requireNonNull(clientHolder, "clientHolder");
        this.instrumentResolver = instrumentResolver;
        this.marketDataProvider = marketDataProvider;
        this.futuresProvider = futuresProvider;
        this.optionsProvider = optionsProvider;
        this.orderCommand = orderCommand;
        this.orderQuery = orderQuery;
        this.sliceOrderCommand = sliceOrderCommand;
        this.bracketOrderProvider = bracketOrderProvider;
        this.gttOrderProvider = gttOrderProvider;
        this.portfolioProvider = portfolioProvider;
        this.marginProvider = marginProvider;
        this.sessionRiskProvider = sessionRiskProvider;
        this.conditionalAlertProvider = conditionalAlertProvider;
        this.webSocketMultiplexer = webSocketMultiplexer;
    }

    /**
     * Legacy constructor — creates all adapters internally.
     *
     * @deprecated Use the multi-adapter constructor for DI environments.
     *             This constructor is preserved only for backward compat and
     *             the {@link #create(DhanConnectionSettings, IdempotencyCachePort)} factory.
     */
    @Deprecated
    public DhanBrokerConnection(
            DhanConnectionSettings settings,
            com.tradej.broker.core.rate.MultiBucketRateLimiter rateLimiter,
            IdempotencyCachePort idempotencyCachePort
    ) {
        DhanTokenProvider tokenProvider = new DhanTokenManager(settings);
        DhanClientHolder clientHolder = new DhanClientHolder(settings, tokenProvider);
        DhanInstrumentResolver instrumentResolver = new InMemoryInstrumentResolver();
        DhanRetryExecutor resilienceExecutor = new DhanRetryExecutor(rateLimiter, new CircuitBreaker());
        DhanAuthenticatedHttpClient httpClient = new DhanAuthenticatedHttpClient(tokenProvider, settings);
        DhanApiUrlResolver apiUrlResolver = new DhanApiUrlResolver(settings);
        DhanRestOrderClient restOrderClient = new DhanRestOrderClient(httpClient, settings, apiUrlResolver, resilienceExecutor);
        DhanHistoricalDataClient historicalDataClient = new DhanHistoricalDataClient(httpClient, apiUrlResolver, resilienceExecutor);
        DhanHistoricalDataMapper historicalDataMapper = new DhanHistoricalDataMapper();
        this.clientHolder = clientHolder;
        this.instrumentResolver = instrumentResolver;
        this.marketDataProvider = new DhanMarketDataProvider(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                historicalDataClient,
                historicalDataMapper
        );
        this.futuresProvider = new DhanFuturesAdapter(instrumentResolver);
        DhanOptionChainClient optionChainClient = new DhanOptionChainClient(httpClient, apiUrlResolver, resilienceExecutor);
        DhanRollingOptionClient rollingOptionClient = new DhanRollingOptionClient(httpClient, apiUrlResolver, resilienceExecutor);
        this.optionsProvider = new DhanOptionsAdapter(
                instrumentResolver,
                optionChainClient,
                rollingOptionClient,
                new OptionExpiryCache(5L),
                resilienceExecutor
        );
        this.orderCommand = new DhanOrderCommandAdapter(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                settings,
                restOrderClient,
                idempotencyCachePort
        );
        this.orderQuery = new DhanOrderQueryAdapter(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                settings,
                restOrderClient
        );
        this.sliceOrderCommand = new DhanSliceOrderAdapter(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                settings,
                restOrderClient,
                this.orderCommand
        );
        this.bracketOrderProvider = new DhanBracketOrderAdapter(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                settings,
                restOrderClient
        );
        this.gttOrderProvider = new DhanGttOrderAdapter(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                settings,
                restOrderClient
        );
        this.portfolioProvider = new DhanPortfolioProvider(
                clientHolder,
                instrumentResolver,
                resilienceExecutor
        );
        this.marginProvider = new DhanMarginProvider(
                clientHolder,
                instrumentResolver,
                resilienceExecutor,
                httpClient,
                apiUrlResolver,
                settings
        );
        this.sessionRiskProvider = new DhanSessionRiskProvider(httpClient, apiUrlResolver, resilienceExecutor);
        this.conditionalAlertProvider = new DhanConditionalAlertProvider(instrumentResolver, httpClient, apiUrlResolver, resilienceExecutor);
        this.webSocketMultiplexer = new DhanWebSocketMultiplexer(
                clientHolder,
                instrumentResolver,
                settings,
                new EventMetadataFactory(new LiveTradingClock()),
                null,
                tokenProvider
        );
    }

    @Override
    public MarketDataProvider marketData() {
        return marketDataProvider;
    }

    @Override
    public FuturesProvider futures() {
        return futuresProvider;
    }

    @Override
    public OptionsProvider options() {
        return optionsProvider;
    }

    @Override
    public OrderCommand orders() {
        return orderCommand;
    }

    @Override
    public OrderQuery orderQuery() {
        return orderQuery;
    }

    @Override
    public SliceOrderCommand sliceOrders() {
        return sliceOrderCommand;
    }

    @Override
    public BracketOrderProvider bracketOrders() {
        return bracketOrderProvider;
    }

    @Override
    public GttOrderProvider gttOrders() {
        return gttOrderProvider;
    }

    @Override
    public PortfolioProvider portfolio() {
        return portfolioProvider;
    }

    @Override
    public MarginProvider margin() {
        return marginProvider;
    }

    @Override
    public SessionRiskProvider sessionRisk() {
        return sessionRiskProvider;
    }

    @Override
    public ConditionalAlertProvider alerts() {
        return conditionalAlertProvider;
    }

    @Override
    public InstrumentResolver instruments() {
        return instrumentResolver;
    }

    @Override
    public WebSocketMultiplexer websocket() {
        return webSocketMultiplexer;
    }

    @Override
    public void connect() {
        webSocketMultiplexer.connect();
    }

    @Override
    public void disconnect() {
        webSocketMultiplexer.disconnect();
        clientHolder.close();
    }

    @Override
    public void loadInstrumentCatalog(Path catalogPath) {
        instrumentResolver.loadCatalog(catalogPath);
    }

    /**
     * Returns the hardcoded default {@link BrokerCapabilities} for the Dhan
     * live trading environment.
     *
     * <p>Delegates to {@link DhanBrokerStartup#defaultCapabilities()}.
     *
     * @return default capabilities for all known Dhan venues
     */
    public static BrokerCapabilities defaultCapabilities() {
        return DhanBrokerStartup.defaultCapabilities();
    }

    public Path loadDailyInstrumentCatalog(Path cacheDirectory, boolean forceRefresh) {
        return DhanBrokerStartup.loadDailyInstrumentCatalog(instrumentResolver, cacheDirectory, forceRefresh);
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) {
            return Optional.empty();
        }
        if (capabilityClass.isInstance(marketDataProvider)) {
            return Optional.of(capabilityClass.cast(marketDataProvider));
        }
        if (capabilityClass.isInstance(futuresProvider)) {
            return Optional.of(capabilityClass.cast(futuresProvider));
        }
        if (capabilityClass.isInstance(optionsProvider)) {
            return Optional.of(capabilityClass.cast(optionsProvider));
        }
        if (capabilityClass.isInstance(orderCommand)) {
            return Optional.of(capabilityClass.cast(orderCommand));
        }
        if (capabilityClass.isInstance(orderQuery)) {
            return Optional.of(capabilityClass.cast(orderQuery));
        }
        if (capabilityClass.isInstance(sliceOrderCommand)) {
            return Optional.of(capabilityClass.cast(sliceOrderCommand));
        }
        if (capabilityClass.isInstance(bracketOrderProvider)) {
            return Optional.of(capabilityClass.cast(bracketOrderProvider));
        }
        if (capabilityClass.isInstance(gttOrderProvider)) {
            return Optional.of(capabilityClass.cast(gttOrderProvider));
        }
        if (capabilityClass.isInstance(portfolioProvider)) {
            return Optional.of(capabilityClass.cast(portfolioProvider));
        }
        if (capabilityClass.isInstance(marginProvider)) {
            return Optional.of(capabilityClass.cast(marginProvider));
        }
        if (capabilityClass.isInstance(sessionRiskProvider)) {
            return Optional.of(capabilityClass.cast(sessionRiskProvider));
        }
        if (capabilityClass.isInstance(conditionalAlertProvider)) {
            return Optional.of(capabilityClass.cast(conditionalAlertProvider));
        }
        if (capabilityClass.isInstance(instrumentResolver)) {
            return Optional.of(capabilityClass.cast(instrumentResolver));
        }
        if (capabilityClass.isInstance(webSocketMultiplexer)) {
            return Optional.of(capabilityClass.cast(webSocketMultiplexer));
        }
        if (OptionsCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(OPTIONS_CAPABLE));
        }
        if (FuturesCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(FUTURES_CAPABLE));
        }
        if (MarginCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(MARGIN_CAPABLE));
        }
        if (AlertCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(ALERT_CAPABLE));
        }
        if (AdvancedOrderCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(ADVANCED_ORDER_CAPABLE));
        }
        return Optional.empty();
    }
}
