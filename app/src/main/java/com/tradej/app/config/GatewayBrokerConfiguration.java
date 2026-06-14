package com.tradej.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerRouter;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.composition.BrokerComposition;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.config.GatewayProperties;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import com.tradej.historical.service.BrokerHistoricalQueryService;
import com.tradej.broker.upstox.expired.BrokerExpiredOptionQueryService;
import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class GatewayBrokerConfiguration {

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
                BrokerSource source = BrokerSource.parse(composition.profile().brokerType().name());
                return BrokerGateway.of(source, composition.brokerConnection());
            }
            Map<BrokerSource, IBrokerConnection> connections = new LinkedHashMap<>();
            brokerConnection.orderedStream().forEach(conn -> {
                connections.putIfAbsent(conn.source(), conn);
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
                return BrokerSource.parse(composition.profile().brokerType().name());
            }
            return BrokerSource.DHAN;
        }
    }

    // ── Gateway application configuration ──

    @Configuration
    @EnableScheduling
    static class GatewayAppConfig {

        @Bean
        GatewayReplayCommandProcessor gatewayReplayCommandProcessor(
                com.tradej.replay.engine.ReplayController replayController,
                ObjectMapper objectMapper
        ) {
            return new com.tradej.gateway.websocket.DefaultReplayCommandProcessor(
                    new com.tradej.gateway.websocket.DefaultReplayCommandProcessor.ReplayControllerAdapter() {
                        @Override public void start(String symbol, String interval, long fromMs, long toMs) {
                            replayController.start(java.util.List.of());
                        }
                        @Override public void pause() { replayController.pause(); }
                        @Override public void resume() { replayController.play(); }
                        @Override public void step(int n) {
                            for (int i = 0; i < n; i++) if (!replayController.step()) break;
                        }
                        @Override public void stop() { replayController.stop(); }
                    },
                    objectMapper
            );
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
        Boolean gatewayEventBusRegistrar(GatewayEventBridge bridge, EventBus eventBus) {
            bridge.register(eventBus);
            log.info("Gateway event bridge registered with event bus");
            return Boolean.TRUE;
        }
    }
}
