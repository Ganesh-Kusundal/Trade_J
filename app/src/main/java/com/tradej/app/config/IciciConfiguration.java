package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.model.MarketSessionPolicy;
import com.tradej.broker.api.model.VenueCapability;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;
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
import com.tradej.broker.icici.config.IciciAuthMode;
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
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "icici")
public class IciciConfiguration {

    @Bean
    BreezeConnectionSettings iciciConnectionSettings(TradingProperties properties) {
        TradingProperties.IciciProperties cfg = properties.icici();
        if (cfg == null) {
            throw new IllegalStateException("trade.icici configuration is required when broker-type=icici");
        }
        return BreezeConnectionSettings.withDefaults(
                cfg.appKey(),
                cfg.secretKey(),
                cfg.sessionToken(),
                cfg.authMode(),
                Path.of(cfg.totpSecretFile()),
                Path.of(cfg.usernameFile()),
                Path.of(cfg.passwordFile()),
                Path.of(cfg.apiSessionFile()),
                Path.of(cfg.tokenStateFile()),
                cfg.ordersEnabled(),
                cfg.refreshBufferMinutes(),
                cfg.loginRedirectPort(),
                cfg.loginRedirectPath(),
                cfg.browserHeadless(),
                cfg.browserLoginTimeoutSeconds()
        );
    }

    @Bean
    HttpClient iciciJavaHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    BreezeTokenManager iciciTokenManager(BreezeConnectionSettings iciciConnectionSettings) {
        return new BreezeTokenManager(iciciConnectionSettings);
    }

    @Bean
    BreezeTokenProvider iciciTokenProvider(BreezeTokenManager iciciTokenManager) {
        return iciciTokenManager;
    }

    @Bean
    BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient(BreezeTokenProvider iciciTokenProvider) {
        return new BreezeAuthenticatedHttpClient(iciciTokenProvider);
    }

    @Bean
    MultiBucketRateLimiter iciciRateLimiter() {
        return new MultiBucketRateLimiter(Map.of(
                IciciResilienceExecutor.CATEGORY_DATA, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DATA, 100.0 / 60.0, 100),
                IciciResilienceExecutor.CATEGORY_DAILY, new RateLimitConfig(IciciResilienceExecutor.CATEGORY_DAILY, 5000.0 / 86400.0, 5000),
                "ORDER", new RateLimitConfig("ORDER", 10.0, 10)
        ));
    }

    @Bean
    IciciResilienceExecutor iciciResilienceExecutor(MultiBucketRateLimiter iciciRateLimiter) {
        return new IciciResilienceExecutor(iciciRateLimiter);
    }

    @Bean
    BreezeDomainMapper iciciDomainMapper() {
        return new BreezeDomainMapper();
    }

    @Bean
    BreezeInstrumentLoader iciciInstrumentLoader(HttpClient iciciJavaHttpClient) {
        return new BreezeInstrumentLoader(iciciJavaHttpClient);
    }

    @Bean
    @Primary
    com.tradej.broker.api.port.InstrumentResolver iciciInstrumentResolverPort(BreezeInstrumentResolver iciciInstrumentResolver) {
        return iciciInstrumentResolver;
    }

    @Bean
    BreezeInstrumentResolver iciciInstrumentResolver(BreezeInstrumentLoader iciciInstrumentLoader) {
        return new BreezeInstrumentResolver(iciciInstrumentLoader);
    }

    @Bean
    BreezeMarketDataRestClient iciciMarketDataRestClient(BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient) {
        return new BreezeMarketDataRestClient(iciciAuthenticatedHttpClient);
    }

    @Bean
    BreezeHistoricalRestClient iciciHistoricalRestClient(BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient) {
        return new BreezeHistoricalRestClient(iciciAuthenticatedHttpClient);
    }

    @Bean
    BreezePortfolioRestClient iciciPortfolioRestClient(BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient) {
        return new BreezePortfolioRestClient(iciciAuthenticatedHttpClient);
    }

    @Bean
    BreezeOrderRestClient iciciOrderRestClient(BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient) {
        return new BreezeOrderRestClient(iciciAuthenticatedHttpClient);
    }

    @Bean
    BreezeOptionChainRestClient iciciOptionChainRestClient(BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient) {
        return new BreezeOptionChainRestClient(iciciAuthenticatedHttpClient);
    }

    @Bean
    BreezeHistoricalDataService iciciHistoricalDataService(
            BreezeHistoricalRestClient iciciHistoricalRestClient,
            BreezeDomainMapper iciciDomainMapper,
            IciciResilienceExecutor iciciResilienceExecutor
    ) {
        return new BreezeHistoricalDataService(
                iciciHistoricalRestClient,
                iciciDomainMapper,
                iciciResilienceExecutor
        );
    }

    @Bean
    com.tradej.broker.api.port.MarketDataProvider iciciMarketDataProvider(
            BreezeMarketDataRestClient iciciMarketDataRestClient,
            BreezeHistoricalDataService iciciHistoricalDataService,
            BreezeInstrumentResolver iciciInstrumentResolver,
            BreezeDomainMapper iciciDomainMapper,
            MeterRegistry meterRegistry
    ) {
        var delegate = new IciciMarketDataProvider(
                iciciMarketDataRestClient,
                iciciHistoricalDataService,
                iciciInstrumentResolver,
                iciciDomainMapper
        );
        return new ObservableMarketDataProvider("icici", delegate, meterRegistry);
    }

    @Bean
    com.tradej.broker.api.port.PortfolioProvider iciciPortfolioProvider(
            BreezePortfolioRestClient iciciPortfolioRestClient
    ) {
        return new IciciPortfolioProvider(iciciPortfolioRestClient);
    }

    @Bean
    com.tradej.broker.api.port.OrderCommand iciciOrderCommand(
            BreezeOrderRestClient iciciOrderRestClient,
            BreezeDomainMapper iciciDomainMapper,
            BreezeInstrumentResolver iciciInstrumentResolver,
            BreezeConnectionSettings iciciConnectionSettings,
            MeterRegistry meterRegistry
    ) {
        var delegate = new IciciOrderCommandAdapter(
                iciciOrderRestClient,
                iciciDomainMapper,
                iciciInstrumentResolver,
                iciciConnectionSettings
        );
        return new ObservableOrderCommand("icici", delegate, meterRegistry);
    }

    @Bean
    com.tradej.broker.api.port.OrderQuery iciciOrderQuery(
            BreezeOrderRestClient iciciOrderRestClient,
            BreezeDomainMapper iciciDomainMapper
    ) {
        return new IciciOrderQueryAdapter(iciciOrderRestClient, iciciDomainMapper);
    }

    @Bean
    com.tradej.broker.api.port.OptionsProvider iciciOptionsProvider(
            BreezeOptionChainRestClient iciciOptionChainRestClient,
            BreezeInstrumentResolver iciciInstrumentResolver,
            BreezeDomainMapper iciciDomainMapper
    ) {
        return new IciciOptionsProvider(iciciOptionChainRestClient, iciciInstrumentResolver, iciciDomainMapper);
    }

    @Bean
    com.tradej.broker.api.port.FuturesProvider iciciFuturesProvider(
            BreezeInstrumentResolver iciciInstrumentResolver
    ) {
        return new IciciFuturesProvider(iciciInstrumentResolver);
    }

    @Bean
    com.tradej.broker.api.port.MarginProvider iciciMarginProvider(
            BreezeAuthenticatedHttpClient iciciAuthenticatedHttpClient,
            BreezeInstrumentResolver iciciInstrumentResolver,
            BreezeDomainMapper iciciDomainMapper
    ) {
        return new IciciMarginProvider(iciciAuthenticatedHttpClient, iciciInstrumentResolver, iciciDomainMapper);
    }

    @Bean
    BreezeWebSocketMultiplexer iciciWebSocketMultiplexer(
            BreezeTokenProvider iciciTokenProvider,
            BreezeInstrumentResolver iciciInstrumentResolver,
            EventMetadataFactory metadataFactory
    ) {
        return new BreezeWebSocketMultiplexer(iciciTokenProvider, iciciInstrumentResolver, metadataFactory);
    }

    @Bean
    @Primary
    IBrokerConnection iciciBrokerConnection(
            com.tradej.broker.api.port.MarketDataProvider iciciMarketDataProvider,
            com.tradej.broker.api.port.FuturesProvider iciciFuturesProvider,
            com.tradej.broker.api.port.OptionsProvider iciciOptionsProvider,
            com.tradej.broker.api.port.OrderCommand iciciOrderCommand,
            com.tradej.broker.api.port.OrderQuery iciciOrderQuery,
            com.tradej.broker.api.port.PortfolioProvider iciciPortfolioProvider,
            com.tradej.broker.api.port.MarginProvider iciciMarginProvider,
            BreezeInstrumentResolver iciciInstrumentResolver,
            BreezeWebSocketMultiplexer iciciWebSocketMultiplexer
    ) {
        return new IciciBrokerConnection(
                iciciMarketDataProvider,
                iciciFuturesProvider,
                iciciOptionsProvider,
                iciciOrderCommand,
                iciciOrderQuery,
                iciciPortfolioProvider,
                iciciMarginProvider,
                iciciInstrumentResolver,
                iciciWebSocketMultiplexer
        );
    }

    @Bean
    BrokerCapabilities iciciBrokerCapabilities() {
        MarketSessionPolicy session = new MarketSessionPolicy(LocalTime.of(9, 15), LocalTime.of(15, 30), false);
        return new BrokerCapabilities(Map.of(
                ExchangeSegment.NSE_EQ, VenueCapability.withSyntheticAdvancedOrders(
                        ExchangeSegment.NSE_EQ,
                        EnumSet.of(FeedMode.TICKER, FeedMode.QUOTE),
                        false,
                        false,
                        false,
                        session
                ),
                ExchangeSegment.NSE_FNO, VenueCapability.withSyntheticAdvancedOrders(
                        ExchangeSegment.NSE_FNO,
                        EnumSet.of(FeedMode.TICKER, FeedMode.QUOTE),
                        false,
                        false,
                        true,
                        session
                )
        ));
    }

    @Bean
    BrokerTransportCapabilities iciciBrokerTransportCapabilities(BreezeConnectionSettings iciciConnectionSettings) {
        return new BrokerTransportCapabilities(
                true,
                iciciConnectionSettings.ordersEnabled(),
                true,
                true,
                false
        );
    }
}
