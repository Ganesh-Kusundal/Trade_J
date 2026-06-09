package com.tradej.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.model.MarketSessionPolicy;
import com.tradej.broker.api.model.VenueCapability;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.expired.BrokerExpiredOptionQueryService;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionMapper;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxExpiredInstrumentRestClient;
import com.tradej.broker.upstox.resilience.UpstoxRetryExecutor;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerRouter;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.marketdata.LivePnlService;
import com.tradej.execution.marketdata.MarketDepthOrchestrator;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.config.GatewayProperties;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import com.tradej.historical.service.BrokerHistoricalQueryService;
import com.tradej.options.service.OptionStrikeResolver;
import com.tradej.replay.engine.CandleReplaySession;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified broker configuration consolidating all broker-agnostic, broker-specific,
 * gateway, and market data wiring into a single file with isolated inner classes.
 */
@Configuration
public class BrokerConfiguration {

    // ── Broker-agnostic beans ──

    @Bean
    CaffeineIdempotencyCache idempotencyCache() {
        return new CaffeineIdempotencyCache();
    }

    @Bean
    @Primary
    OrderIdentityRegistry orderIdentityRegistry() {
        return new OrderIdentityRegistry();
    }

    @Bean
    BrokerCapabilities brokerCapabilities(TradingProperties properties) {
        return PropertiesBrokerCapabilities.from(properties.venues());
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

    // ── Dhan-specific configuration ──

    @Configuration
    @ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
    static class DhanConfig {

        @Bean
        @Primary
        MultiBucketRateLimiter multiBucketRateLimiter() {
            return DhanProtocolConstants.defaultRateLimiter();
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
                    null,
                    false
            );
        }

        @Bean
        DhanTokenProvider dhanTokenProvider(DhanConnectionSettings settings) {
            return new DhanTokenManager(settings);
        }

        @Bean
        BrokerComposition brokerComposition(TradingProperties properties, CaffeineIdempotencyCache idempotencyCache) {
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
            return BrokerComposition.create(profile, idempotencyCache);
        }

        @Bean
        BrokerTransportCapabilities dhanTransportCapabilities() {
            return BrokerTransportCapabilities.dhanLive();
        }

        @Bean(name = "brokerConnection")
        @Primary
        IBrokerConnection brokerConnection(BrokerComposition composition) {
            return composition.brokerConnection();
        }

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
    }

    // ── Upstox-specific configuration ──

    @Configuration
    @ConditionalOnExpression("'${trade.broker-type:}' == 'upstox'")
    static class UpstoxConfig {

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

    // ── ICICI-specific configuration ──

    @Configuration
    @ConditionalOnExpression("'${trade.broker-type:}' == 'icici' || '${trade.broker-type:}' == 'gateway'")
    static class IciciConfig {

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
        BreezeTokenProvider iciciTokenProvider(BreezeConnectionSettings iciciConnectionSettings) {
            return new com.tradej.broker.icici.auth.BreezeTokenManager(iciciConnectionSettings);
        }

        @Bean
        IciciBrokerConnection iciciBrokerConnection(TradingProperties properties) {
            TradingProperties.IciciProperties cfg = properties.icici();
            BrokerProfile.IciciConfig iciciConfig = new BrokerProfile.IciciConfig(
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
            return (IciciBrokerConnection) com.tradej.composition.IciciBrokerFactory.create(iciciConfig);
        }

        @Bean(name = "iciciBrokerConnectionBean")
        IBrokerConnection brokerConnectionBean(IciciBrokerConnection conn) {
            return conn;
        }

        @Bean
        MarketDataProvider iciciMarketDataProvider(IciciBrokerConnection conn, MeterRegistry meterRegistry) {
            return new ObservableMarketDataProvider("icici", conn.marketData(), meterRegistry);
        }

        @Bean
        OrderCommand iciciOrderCommand(IciciBrokerConnection conn, MeterRegistry meterRegistry) {
            return new ObservableOrderCommand("icici", conn.orders(), meterRegistry);
        }

        @Bean
        InstrumentResolver iciciInstrumentResolver(IciciBrokerConnection conn) {
            return conn.instruments();
        }

        @Bean
        OrderQuery iciciOrderQuery(IciciBrokerConnection conn) {
            return conn.orderQuery();
        }

        @Bean
        PortfolioProvider iciciPortfolioProvider(IciciBrokerConnection conn) {
            return conn.portfolio();
        }

        @Bean
        MarginProvider iciciMarginProvider(IciciBrokerConnection conn) {
            return conn.margin();
        }

        @Bean
        FuturesProvider iciciFuturesProvider(IciciBrokerConnection conn) {
            return conn.futures();
        }

        @Bean
        OptionsProvider iciciOptionsProvider(IciciBrokerConnection conn) {
            return conn.options();
        }

        @Bean
        WebSocketMultiplexer iciciWebSocketMultiplexer(IciciBrokerConnection conn) {
            return conn.websocket();
        }

        @Bean
        ConditionalAlertProvider iciciConditionalAlertProvider(IciciBrokerConnection conn) {
            return conn.alerts();
        }

        @Bean
        BrokerCapabilities iciciBrokerCapabilities() {
            MarketSessionPolicy session = new MarketSessionPolicy(LocalTime.of(9, 15), LocalTime.of(15, 30), false);
            return new BrokerCapabilities(Map.of(
                    ExchangeSegment.NSE_EQ, VenueCapability.withSyntheticAdvancedOrders(
                            ExchangeSegment.NSE_EQ,
                            EnumSet.of(FeedMode.TICKER, FeedMode.QUOTE),
                            false, false, false, session),
                    ExchangeSegment.NSE_FNO, VenueCapability.withSyntheticAdvancedOrders(
                            ExchangeSegment.NSE_FNO,
                            EnumSet.of(FeedMode.TICKER, FeedMode.QUOTE),
                            false, false, true, session)
            ));
        }

        @Bean
        BrokerTransportCapabilities iciciBrokerTransportCapabilities(BreezeConnectionSettings settings) {
            return new BrokerTransportCapabilities(
                    true, settings.ordersEnabled(), true, true, false);
        }
    }

    // ── Multi-broker gateway configuration ──

    @Configuration
    @ConditionalOnProperty(name = "trade.broker-type", havingValue = "gateway")
    static class GatewayConfig {

        @Bean(name = "loadBalancedBrokerGateway")
        @Primary
        IBrokerConnection loadBalancedBrokerGateway(
                ObjectProvider<IBrokerConnection> brokerConnections,
                ReconnectListenerRegistry reconnectListenerRegistry
        ) {
            List<IBrokerConnection> nodes = brokerConnections.orderedStream().toList();
            if (nodes.isEmpty()) {
                throw new IllegalStateException(
                        "trade.broker-type=gateway requires at least one broker adapter on the classpath "
                                + "(enable dhan/icici/upstox configuration properties)");
            }
            return new LoadBalancedBrokerGateway(nodes, reconnectListenerRegistry);
        }
    }

    // ── Gateway beans ──

    @Configuration
    static class GatewayBeansConfig {

        @Bean
        BrokerGateway brokerGateway(
                ObjectProvider<BrokerComposition> compositionProvider,
                ObjectProvider<IBrokerConnection> brokerConnection
        ) {
            BrokerComposition composition = compositionProvider.getIfAvailable();
            if (composition != null) {
                BrokerSource source = toSource(composition.profile().brokerType());
                return BrokerGateway.of(source, composition.brokerConnection());
            }
            Map<BrokerSource, IBrokerConnection> connections = new LinkedHashMap<>();
            brokerConnection.orderedStream().forEach(conn -> {
                BrokerSource source = identifyBroker(conn);
                connections.putIfAbsent(source, conn);
            });
            if (connections.isEmpty()) {
                throw new IllegalStateException("No IBrokerConnection beans available for gateway");
            }
            return BrokerGateway.fromConnections(connections);
        }

        @Bean
        BrokerRouter brokerRouter(BrokerGateway gateway) {
            return new BrokerRouter(gateway);
        }

        @Bean
        MarketGateway marketGateway(BrokerRouter router) {
            return MarketGateway.create(router);
        }

        @Bean
        BrokerSource activeBrokerSource(ObjectProvider<BrokerComposition> compositionProvider) {
            BrokerComposition composition = compositionProvider.getIfAvailable();
            if (composition != null) {
                return toSource(composition.profile().brokerType());
            }
            return BrokerSource.DHAN;
        }

        private static BrokerSource toSource(com.tradej.composition.config.BrokerProfile.BrokerType type) {
            return switch (type) {
                case DHAN, GATEWAY -> BrokerSource.DHAN;
                case UPSTOX -> BrokerSource.UPSTOX;
                case ICICI -> BrokerSource.ICICI;
            };
        }

        private static BrokerSource identifyBroker(IBrokerConnection conn) {
            String className = conn.getClass().getSimpleName().toLowerCase();
            if (className.contains("dhan")) return BrokerSource.DHAN;
            if (className.contains("upstox")) return BrokerSource.UPSTOX;
            if (className.contains("icici") || className.contains("breeze")) return BrokerSource.ICICI;
            if (className.contains("loadbalanced") || className.contains("gateway")) {
                return BrokerSource.DHAN;
            }
            return BrokerSource.SIMULATION;
        }
    }

    // ── Gateway application configuration ──

    @Configuration
    @EnableScheduling
    static class GatewayAppConfig {

        @Bean
        GatewayReplayCommandProcessor gatewayReplayCommandProcessor(
                CandleReplaySession replaySession,
                ObjectMapper objectMapper
        ) {
            return json -> {
                try {
                    JsonNode node = objectMapper.readTree(json);
                    String command = node.path("command").asText("");
                    switch (command) {
                        case "play" -> replaySession.play();
                        case "pause" -> replaySession.pause();
                        case "stop" -> replaySession.stop();
                        case "step" -> replaySession.step();
                        case "speed" -> replaySession.setSpeed(node.path("multiplier").asDouble(1.0));
                        default -> { }
                    }
                } catch (Exception ignored) {
                    // Malformed client command — ignore
                }
            };
        }

        @Bean
        @ConditionalOnBean(GatewayEventBridge.class)
        GatewayPipelineHealthBroadcaster gatewayPipelineHealthBroadcaster(
                GatewayEventBridge bridge,
                RuntimeHealthState runtimeHealthState,
                ObjectProvider<IBrokerConnection> brokerConnection
        ) {
            return new GatewayPipelineHealthBroadcaster(bridge, runtimeHealthState, brokerConnection);
        }

        static final class GatewayPipelineHealthBroadcaster {

            private final GatewayEventBridge bridge;
            private final RuntimeHealthState runtimeHealthState;
            private final ObjectProvider<IBrokerConnection> brokerConnection;

            GatewayPipelineHealthBroadcaster(
                    GatewayEventBridge bridge,
                    RuntimeHealthState runtimeHealthState,
                    ObjectProvider<IBrokerConnection> brokerConnection
            ) {
                this.bridge = bridge;
                this.runtimeHealthState = runtimeHealthState;
                this.brokerConnection = brokerConnection;
            }

            @Scheduled(fixedDelayString = "${tradej.gateway.health-interval-ms:5000}")
            void publishHealth() {
                Map<String, Object> health = new LinkedHashMap<>();
                health.put("type", "PIPELINE_HEALTH");
                health.put("catalogLoaded", runtimeHealthState.catalogLoaded());
                health.put("catalogSize", runtimeHealthState.catalogSize());
                health.put("brokerPreflightPassed", runtimeHealthState.brokerPreflightPassed());
                health.put("startupCompleted", runtimeHealthState.startupCompleted());
                brokerConnection.ifAvailable(conn -> {
                    if (conn instanceof LoadBalancedBrokerGateway gateway) {
                        health.put("brokerNodes", gateway.connectionCount());
                    }
                });
                bridge.publishPipelineHealth(health);
            }
        }
    }

    // ── Broker market data configuration ──

    @Configuration
    static class BrokerMarketDataConfig {

        @Bean
        @ConditionalOnBean(MarketDataProvider.class)
        BrokerHistoricalQueryService brokerHistoricalQueryService(MarketDataProvider marketDataProvider) {
            return new BrokerHistoricalQueryService(marketDataProvider);
        }

        @Bean
        @ConditionalOnBean(UpstoxExpiredOptionService.class)
        BrokerExpiredOptionQueryService brokerExpiredOptionQueryService(
                UpstoxExpiredOptionService upstoxExpiredOptionService
        ) {
            return new BrokerExpiredOptionQueryService(upstoxExpiredOptionService);
        }
    }

    // ── Gateway WebSocket configuration ──

    @Configuration
    @EnableWebSocket
    @EnableConfigurationProperties(GatewayProperties.class)
    @ConditionalOnProperty(name = "tradej.gateway.enabled", havingValue = "true", matchIfMissing = false)
    static class GatewayWebSocketConfiguration implements WebSocketConfigurer {

        private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketConfiguration.class);

        private final GatewayProperties properties;

        public GatewayWebSocketConfiguration(GatewayProperties properties) {
            this.properties = properties;
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(gatewayWebSocketHandler(gatewayTopicRouter(), null), properties.websocketPath())
                    .setAllowedOrigins("*");
            log.info("Gateway WebSocket endpoint registered at {}", properties.websocketPath());
        }

        @Bean(initMethod = "start", destroyMethod = "stop")
        GatewayTopicRouter gatewayTopicRouter() {
            return new GatewayTopicRouter();
        }

        @Bean
        @Lazy
        GatewayWebSocketHandler gatewayWebSocketHandler(
                GatewayTopicRouter router,
                @org.springframework.beans.factory.annotation.Autowired(required = false)
                GatewayReplayCommandProcessor replayCommandProcessor
        ) {
            return new GatewayWebSocketHandler(router, replayCommandProcessor);
        }

        @Bean(destroyMethod = "close")
        GatewayEventBridge gatewayEventBridge(
                GatewayTopicRouter router,
                ObjectMapper objectMapper,
                @org.springframework.beans.factory.annotation.Autowired(required = false) InstrumentResolver instrumentResolver
        ) {
            return new GatewayEventBridge(router, objectMapper, instrumentResolver);
        }

        @Bean
        Object gatewayEventBusRegistrar(GatewayEventBridge bridge, EventBus eventBus) {
            bridge.register(eventBus);
            log.info("Gateway event bridge registered with event bus");
            return bridge;
        }
    }
}
