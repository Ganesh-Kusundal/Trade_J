package com.tradej.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerRouter;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.brokergateway.wiring.BrokerComposition;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.config.GatewayProperties;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import com.tradej.replay.engine.CandleReplaySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Gateway-domain configuration: WebSocket endpoints, routing, beans, and app-level components.
 *
 * <p>Consolidated from:
 * <ul>
 *   <li>{@code GatewayAppConfiguration} — replay processor + pipeline health broadcaster</li>
 *   <li>{@code GatewayBeansConfiguration} — broker gateway, router, market gateway, source</li>
 *   <li>{@code GatewayRoutingConfiguration} — load-balanced multi-broker routing</li>
 *   <li>{@code GatewayWebSocketConfiguration} — WS endpoint registration + topic router</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableWebSocket
@EnableConfigurationProperties(GatewayProperties.class)
@ConditionalOnProperty(name = "tradej.gateway.enabled", havingValue = "true", matchIfMissing = false)
public class GatewayConfiguration implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfiguration.class);

    private final GatewayProperties properties;

    public GatewayConfiguration(GatewayProperties properties) {
        this.properties = properties;
    }

    // ── WebSocket endpoint ──

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
            @Autowired(required = false) GatewayReplayCommandProcessor replayCommandProcessor
    ) {
        return new GatewayWebSocketHandler(router, replayCommandProcessor);
    }

    @Bean(destroyMethod = "close")
    GatewayEventBridge gatewayEventBridge(
            GatewayTopicRouter router,
            ObjectMapper objectMapper,
            @Autowired(required = false) com.tradej.broker.api.port.InstrumentResolver instrumentResolver
    ) {
        return new GatewayEventBridge(router, objectMapper, instrumentResolver);
    }

    @Bean
    Object gatewayEventBusRegistrar(GatewayEventBridge bridge, EventBus eventBus) {
        bridge.register(eventBus);
        log.info("Gateway event bridge registered with event bus");
        return bridge;
    }

    // ── Replay processor ──

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

    // ── Gateway beans (routing, broker gateway, market gateway) ──

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

    // ── Load-balanced multi-broker routing (only when trade.broker-type=gateway) ──

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

    // ── Pipeline health broadcaster ──

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
