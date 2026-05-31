package com.tradej.gateway.config;

import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring configuration for the WebSocket gateway. Registers the
 * {@link GatewayWebSocketHandler}, {@link GatewayTopicRouter},
 * and {@link GatewayEventBridge} when {@code tradej.gateway.enabled=true}.
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(GatewayProperties.class)
@ConditionalOnProperty(name = "tradej.gateway.enabled", havingValue = "true", matchIfMissing = false)
public class GatewayWebSocketConfiguration implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketConfiguration.class);

    private final GatewayProperties properties;

    public GatewayWebSocketConfiguration(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gatewayWebSocketHandler(gatewayTopicRouter()), properties.websocketPath())
                .setAllowedOrigins("*");
        log.info("Gateway WebSocket endpoint registered at {}", properties.websocketPath());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    GatewayTopicRouter gatewayTopicRouter() {
        return new GatewayTopicRouter();
    }

    @Bean
    GatewayWebSocketHandler gatewayWebSocketHandler(GatewayTopicRouter router) {
        return new GatewayWebSocketHandler(router);
    }

    @Bean(destroyMethod = "close")
    GatewayEventBridge gatewayEventBridge(GatewayTopicRouter router, ObjectMapper objectMapper) {
        return new GatewayEventBridge(router, objectMapper);
    }

    @Bean
    GatewayEventBusRegistrar gatewayEventBusRegistrar(
            GatewayEventBridge bridge, EventBus eventBus) {
        return new GatewayEventBusRegistrar(bridge, eventBus);
    }

    /**
     * Registers the bridge with the event bus on startup.
     */
    static class GatewayEventBusRegistrar {

        GatewayEventBusRegistrar(GatewayEventBridge bridge, EventBus eventBus) {
            bridge.register(eventBus);
            log.info("Gateway event bridge registered with event bus");
        }
    }
}
