package com.tradej.app.config;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.config.GatewayProperties;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(GatewayProperties.class)
@ConditionalOnProperty(name = "tradej.gateway.enabled", havingValue = "true", matchIfMissing = false)
public class GatewayWebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketConfig.class);

    private final GatewayProperties properties;

    public GatewayWebSocketConfig(GatewayProperties properties) {
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
