package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.*;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.IciciBrokerFactory;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.marketdata.LivePnlService;
import com.tradej.execution.marketdata.MarketDepthOrchestrator;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.options.service.OptionStrikeResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Unified broker configuration that resolves the competing dependency graph problem.
 *
 * <p><b>Problem</b>: Previously, three separate configuration classes created conflicting beans:
 * <ul>
 *   <li>{@link DhanBrokerConfiguration} - Created BrokerComposition for Dhan</li>
 *   <li>{@link IciciConfiguration} - Created IciciBrokerConnection directly</li>
 *   <li>{@link BrokerConfiguration} - Created port beans and another brokerConnection</li>
 * </ul>
 *
 * <p><b>Solution</b>: Single configuration with conditional broker selection based on
 * {@code trade.broker-type} property:
 * <ul>
 *   <li>{@code dhan} - Uses Dhan via BrokerComposition</li>
 *   <li>{@code icici} - Uses ICICI via IciciBrokerFactory</li>
 *   <li>{@code upstox} - Uses Upstox via BrokerComposition</li>
 *   <li>{@code gateway} - Uses BrokerComposition with auto-detection</li>
 * </ul>
 *
 * <p>All port beans (MarketDataProvider, OrderCommand, etc.) are derived from the
 * single active {@link IBrokerConnection}, ensuring no conflicts.
 */
@Configuration
public class UnifiedBrokerConfiguration {

    // ─── Shared Infrastructure Beans ─────────────────────────────────────

    @Bean
    CaffeineIdempotencyCache idempotencyCache() {
        return new CaffeineIdempotencyCache();
    }

    @Bean
    @Primary
    OrderIdentityRegistry orderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    // ─── Dhan-Specific Beans ────────────────────────────────────────────

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
    MultiBucketRateLimiter multiBucketRateLimiter() {
        return DhanProtocolConstants.defaultRateLimiter();
    }

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
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
    @ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
    DhanTokenProvider dhanTokenProvider(DhanConnectionSettings settings) {
        return new DhanTokenManager(settings);
    }

    // ─── ICICI-Specific Beans ───────────────────────────────────────────

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:}' == 'icici'")
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
                java.nio.file.Path.of(cfg.totpSecretFile()),
                java.nio.file.Path.of(cfg.usernameFile()),
                java.nio.file.Path.of(cfg.passwordFile()),
                java.nio.file.Path.of(cfg.apiSessionFile()),
                java.nio.file.Path.of(cfg.tokenStateFile()),
                cfg.ordersEnabled(),
                cfg.refreshBufferMinutes(),
                cfg.loginRedirectPort(),
                cfg.loginRedirectPath(),
                cfg.browserHeadless(),
                cfg.browserLoginTimeoutSeconds()
        );
    }

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:}' == 'icici'")
    BreezeTokenProvider iciciTokenProvider(BreezeConnectionSettings settings) {
        return new BreezeTokenManager(settings);
    }

    // ─── Broker Connection Selection ────────────────────────────────────

    /**
     * Dhan broker connection via BrokerComposition.
     * Active when trade.broker-type=dhan or gateway.
     */
    @Bean(name = "brokerConnection")
    @Primary
    @ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
    IBrokerConnection dhanBrokerConnection(TradingProperties properties, CaffeineIdempotencyCache idempotencyCache) {
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
        BrokerComposition composition = BrokerComposition.create(profile, idempotencyCache);
        return composition.brokerConnection();
    }

    /**
     * ICICI broker connection via IciciBrokerFactory.
     * Active when trade.broker-type=icici.
     */
    @Bean(name = "brokerConnection")
    @Primary
    @ConditionalOnExpression("'${trade.broker-type:}' == 'icici'")
    IBrokerConnection iciciBrokerConnection(TradingProperties properties) {
        TradingProperties.IciciProperties cfg = properties.icici();
        if (cfg == null) {
            throw new IllegalStateException("trade.icici configuration is required when broker-type=icici");
        }
        
        BrokerProfile.IciciConfig iciciConfig = new BrokerProfile.IciciConfig(
                cfg.appKey(),
                cfg.secretKey(),
                cfg.sessionToken(),
                cfg.authMode(),
                java.nio.file.Path.of(cfg.totpSecretFile()),
                java.nio.file.Path.of(cfg.usernameFile()),
                java.nio.file.Path.of(cfg.passwordFile()),
                java.nio.file.Path.of(cfg.apiSessionFile()),
                java.nio.file.Path.of(cfg.tokenStateFile()),
                cfg.ordersEnabled(),
                cfg.refreshBufferMinutes(),
                cfg.loginRedirectPort(),
                cfg.loginRedirectPath(),
                cfg.browserHeadless(),
                cfg.browserLoginTimeoutSeconds()
        );
        
        return (IciciBrokerConnection) IciciBrokerFactory.create(iciciConfig);
    }

    /**
     * Upstox broker connection via BrokerComposition.
     * Active when trade.broker-type=upstox.
     */
    @Bean(name = "brokerConnection")
    @Primary
    @ConditionalOnExpression("'${trade.broker-type:}' == 'upstox'")
    IBrokerConnection upstoxBrokerConnection(TradingProperties properties, CaffeineIdempotencyCache idempotencyCache) {
        TradingProperties.UpstoxProperties cfg = properties.upstox();
        if (cfg == null) {
            throw new IllegalStateException("trade.upstox configuration is required when broker-type=upstox");
        }
        
        BrokerProfile.UpstoxConfig upstoxConfig = new BrokerProfile.UpstoxConfig(
                cfg.apiKey(),
                cfg.apiSecret(),
                cfg.redirectUri(),
                cfg.accessToken(),
                cfg.refreshToken(),
                cfg.environment(),
                cfg.restBaseUrl(),
                cfg.wsBaseUrl(),
                cfg.authMode(),
                java.nio.file.Path.of(cfg.tokenStateFile()),
                cfg.refreshBufferMinutes(),
                cfg.ordersEnabled()
        );
        
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.UPSTOX, upstoxConfig, null, null);
        BrokerComposition composition = BrokerComposition.create(profile, idempotencyCache);
        return composition.brokerConnection();
    }

    // ─── Port Beans (Derived from Active IBrokerConnection) ─────────────

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

    // ─── Capability Beans ───────────────────────────────────────────────

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
    BrokerTransportCapabilities dhanTransportCapabilities() {
        return BrokerTransportCapabilities.dhanLive();
    }

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:}' == 'icici'")
    BrokerCapabilities iciciBrokerCapabilities() {
        return PropertiesBrokerCapability.from(
                java.util.Map.of(
                        com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                        com.tradej.broker.api.model.VenueCapability.withSyntheticAdvancedOrders(
                                com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                                java.util.EnumSet.of(com.tradej.core.domain.value.FeedMode.TICKER, com.tradej.core.domain.value.FeedMode.QUOTE),
                                false, false, false,
                                new com.tradej.broker.api.model.MarketSessionPolicy(
                                        java.time.LocalTime.of(9, 15),
                                        java.time.LocalTime.of(15, 30),
                                        false)),
                        com.tradej.core.domain.value.ExchangeSegment.NSE_FNO,
                        com.tradej.broker.api.model.VenueCapability.withSyntheticAdvancedOrders(
                                com.tradej.core.domain.value.ExchangeSegment.NSE_FNO,
                                java.util.EnumSet.of(com.tradej.core.domain.value.FeedMode.TICKER, com.tradej.core.domain.value.FeedMode.QUOTE),
                                false, false, true,
                                new com.tradej.broker.api.model.MarketSessionPolicy(
                                        java.time.LocalTime.of(9, 15),
                                        java.time.LocalTime.of(15, 30),
                                        false))
                ));
    }

    @Bean
    @ConditionalOnExpression("'${trade.broker-type:}' == 'icici'")
    BrokerTransportCapabilities iciciBrokerTransportCapabilities(BreezeConnectionSettings settings) {
        return new BrokerTransportCapabilities(
                true, settings.ordersEnabled(), true, true, false);
    }

    @Bean
    BrokerCapabilities brokerCapabilities(TradingProperties properties) {
        return PropertiesBrokerCapability.from(properties.venues());
    }

    // ─── Dependent Service Beans ────────────────────────────────────────

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
