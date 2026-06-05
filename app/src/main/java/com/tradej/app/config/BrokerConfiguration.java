package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.adapter.DhanMarketDataProvider;
import com.tradej.broker.dhan.adapter.DhanOrderCommandAdapter;
import com.tradej.broker.dhan.adapter.DhanOrderQueryAdapter;
import com.tradej.broker.dhan.adapter.DhanPortfolioProvider;
import com.tradej.broker.dhan.adapter.InMemoryInstrumentResolver;
import com.tradej.broker.dhan.validator.DhanOrderValidator;
import com.tradej.broker.dhan.adapter.DhanFuturesAdapter;
import com.tradej.broker.dhan.adapter.DhanBracketOrderAdapter;
import com.tradej.broker.dhan.adapter.DhanConditionalAlertProvider;
import com.tradej.broker.dhan.adapter.DhanGttOrderAdapter;
import com.tradej.broker.dhan.adapter.DhanMarginProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.broker.dhan.historical.DhanHistoricalDataMapper;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.adapter.DhanOptionsAdapter;
import com.tradej.broker.dhan.adapter.DhanSessionRiskProvider;
import com.tradej.broker.dhan.adapter.DhanSliceOrderAdapter;
import com.tradej.broker.dhan.options.DhanOptionChainClient;
import com.tradej.broker.dhan.options.DhanRollingOptionClient;
import com.tradej.broker.dhan.options.OptionExpiryCache;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.broker.dhan.websocket.DhanWebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.app.service.broker.LivePnlService;
import com.tradej.app.service.broker.MarketDepthOrchestrator;
import com.tradej.app.service.broker.OptionStrikeResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


/**
 * Configures Dhan broker connection, rate limiting, idempotency cache, and all
 * per-adapter beans.
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
    com.tradej.execution.identity.OrderIdentityRegistry orderIdentityRegistry() {
        return new com.tradej.execution.identity.OrderIdentityRegistry();
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
                null,  // depthWsUrl - will use default
                false  // killSwitchTestEnabled - default
        );
    }

    @Bean
    DhanTokenProvider dhanTokenProvider(DhanConnectionSettings settings) {
        return new DhanTokenManager(settings);
    }

    @Bean
    DhanClientHolder dhanClientHolder(DhanConnectionSettings settings, DhanTokenProvider tokenProvider) {
        return new DhanClientHolder(settings, tokenProvider);
    }

    @Bean
    DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient(
            DhanTokenProvider tokenProvider,
            DhanConnectionSettings settings
    ) {
        return new DhanAuthenticatedHttpClient(tokenProvider, settings);
    }

    @Bean
    DhanRetryExecutor dhanRetryExecutor(MultiBucketRateLimiter rateLimiter) {
        return new DhanRetryExecutor(rateLimiter, new CircuitBreaker());
    }

    @Bean
    DhanApiUrlResolver dhanApiUrlResolver(DhanConnectionSettings settings) {
        return new DhanApiUrlResolver(settings);
    }

    @Bean
    DhanRestOrderClient dhanRestOrderClient(
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanConnectionSettings dhanConnectionSettings,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanRestOrderClient(
                dhanAuthenticatedHttpClient,
                dhanConnectionSettings,
                dhanApiUrlResolver,
                dhanRetryExecutor
        );
    }

    @Bean
    DhanInstrumentResolver dhanInstrumentResolver() {
        return new InMemoryInstrumentResolver();
    }

    @Bean
    DhanHistoricalDataClient dhanHistoricalDataClient(
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanHistoricalDataClient(dhanAuthenticatedHttpClient, dhanApiUrlResolver, dhanRetryExecutor);
    }

    @Bean
    DhanHistoricalDataMapper dhanHistoricalDataMapper() {
        return new DhanHistoricalDataMapper();
    }

    @Bean
    MarketDataProvider marketDataProvider(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanHistoricalDataClient dhanHistoricalDataClient,
            DhanHistoricalDataMapper dhanHistoricalDataMapper,
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            MeterRegistry meterRegistry
    ) {
        MarketDataProvider delegate = new DhanMarketDataProvider(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanHistoricalDataClient,
                dhanHistoricalDataMapper,
                dhanAuthenticatedHttpClient,
                dhanApiUrlResolver
        );
        return new ObservableMarketDataProvider("dhan", delegate, meterRegistry);
    }

    @Bean
    DhanOrderValidator dhanOrderValidator(
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanConnectionSettings dhanConnectionSettings,
            MarketDataProvider marketDataProvider
    ) {
        return new DhanOrderValidator(dhanInstrumentResolver, dhanConnectionSettings, marketDataProvider);
    }

    @Bean
    FuturesProvider futuresProvider(DhanInstrumentResolver dhanInstrumentResolver) {
        return new DhanFuturesAdapter(dhanInstrumentResolver);
    }

    @Bean
    DhanOptionChainClient dhanOptionChainClient(
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanOptionChainClient(dhanAuthenticatedHttpClient, dhanApiUrlResolver, dhanRetryExecutor);
    }

    @Bean
    DhanRollingOptionClient dhanRollingOptionClient(
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanRollingOptionClient(dhanAuthenticatedHttpClient, dhanApiUrlResolver, dhanRetryExecutor);
    }

    @Bean
    OptionExpiryCache optionExpiryCache(TradingProperties properties) {
        return new OptionExpiryCache(properties.broker().optionExpiryCacheTtlMinutes());
    }

    @Bean
    OptionsProvider optionsProvider(
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanOptionChainClient dhanOptionChainClient,
            DhanRollingOptionClient dhanRollingOptionClient,
            OptionExpiryCache optionExpiryCache,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanOptionsAdapter(
                dhanInstrumentResolver,
                dhanOptionChainClient,
                dhanRollingOptionClient,
                optionExpiryCache,
                dhanRetryExecutor
        );
    }

    @Bean
    OrderCommand orderCommand(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanConnectionSettings dhanConnectionSettings,
            DhanRestOrderClient dhanRestOrderClient,
            IdempotencyCachePort idempotencyCache,
            DhanOrderValidator dhanOrderValidator,
            MeterRegistry meterRegistry
    ) {
        OrderCommand delegate = new DhanOrderCommandAdapter(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanConnectionSettings,
                dhanRestOrderClient,
                idempotencyCache,
                dhanOrderValidator
        );
        return new ObservableOrderCommand("dhan", delegate, meterRegistry);
    }

    @Bean
    OrderQuery orderQuery(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanConnectionSettings dhanConnectionSettings,
            DhanRestOrderClient dhanRestOrderClient
    ) {
        return new DhanOrderQueryAdapter(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanConnectionSettings,
                dhanRestOrderClient
        );
    }

    @Bean
    SliceOrderCommand sliceOrderCommand(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanConnectionSettings dhanConnectionSettings,
            DhanRestOrderClient dhanRestOrderClient,
            OrderCommand orderCommand
    ) {
        return new DhanSliceOrderAdapter(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanConnectionSettings,
                dhanRestOrderClient,
                orderCommand
        );
    }

    @Bean
    BracketOrderProvider bracketOrderProvider(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanConnectionSettings dhanConnectionSettings,
            DhanRestOrderClient dhanRestOrderClient
    ) {
        return new DhanBracketOrderAdapter(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanConnectionSettings,
                dhanRestOrderClient
        );
    }

    @Bean
    GttOrderProvider gttOrderProvider(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanConnectionSettings dhanConnectionSettings,
            DhanRestOrderClient dhanRestOrderClient
    ) {
        return new DhanGttOrderAdapter(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanConnectionSettings,
                dhanRestOrderClient
        );
    }

    @Bean
    PortfolioProvider portfolioProvider(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver
    ) {
        return new DhanPortfolioProvider(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanAuthenticatedHttpClient,
                dhanApiUrlResolver
        );
    }

    @Bean
    MarginProvider marginProvider(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanRetryExecutor dhanRetryExecutor,
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanConnectionSettings dhanConnectionSettings
    ) {
        return new DhanMarginProvider(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanRetryExecutor,
                dhanAuthenticatedHttpClient,
                dhanApiUrlResolver,
                dhanConnectionSettings
        );
    }

    @Bean
    SessionRiskProvider sessionRiskProvider(
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanSessionRiskProvider(dhanAuthenticatedHttpClient, dhanApiUrlResolver, dhanRetryExecutor);
    }

    @Bean
    ConditionalAlertProvider conditionalAlertProvider(
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanAuthenticatedHttpClient dhanAuthenticatedHttpClient,
            DhanApiUrlResolver dhanApiUrlResolver,
            DhanRetryExecutor dhanRetryExecutor
    ) {
        return new DhanConditionalAlertProvider(
                dhanInstrumentResolver,
                dhanAuthenticatedHttpClient,
                dhanApiUrlResolver,
                dhanRetryExecutor
        );
    }

    @Bean
    WebSocketMultiplexer webSocketMultiplexer(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
            DhanConnectionSettings dhanConnectionSettings,
            EventMetadataFactory eventMetadataFactory,
            ReconnectListenerRegistry reconnectListenerRegistry,
            DhanTokenProvider dhanTokenProvider
    ) {
        return new DhanWebSocketMultiplexer(
                dhanClientHolder,
                dhanInstrumentResolver,
                dhanConnectionSettings,
                eventMetadataFactory,
                reconnectListenerRegistry,
                dhanTokenProvider
        );
    }

    @Bean(name = {"brokerConnection", "dhanBrokerConnection"})
    DhanBrokerConnection brokerConnection(
            DhanClientHolder dhanClientHolder,
            DhanInstrumentResolver dhanInstrumentResolver,
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
        return new DhanBrokerConnection(
                dhanClientHolder,
                dhanInstrumentResolver,
                marketDataProvider,
                futuresProvider,
                optionsProvider,
                orderCommand,
                orderQuery,
                sliceOrderCommand,
                bracketOrderProvider,
                gttOrderProvider,
                portfolioProvider,
                marginProvider,
                sessionRiskProvider,
                conditionalAlertProvider,
                webSocketMultiplexer
        );
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
