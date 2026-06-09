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
