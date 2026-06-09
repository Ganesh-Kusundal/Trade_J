package com.tradej.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.health.GatewayHealthIndicator;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Trade-J WebSocket gateway module.
 *
 * <p>Registers gateway core beans ({@link GatewayTopicRouter}, {@link GatewayEventBridge},
 * {@link GatewayWebSocketHandler}) when {@code tradej.gateway.enabled=true}.
 *
 * <p>Beans are conditional on missing so that user-defined overrides take precedence.
 */
@AutoConfiguration
@ConditionalOnClass(GatewayTopicRouter.class)
@ConditionalOnProperty(name = "tradej.gateway.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayAutoConfiguration.class);

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public GatewayTopicRouter gatewayTopicRouter() {
        return new GatewayTopicRouter();
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayWebSocketHandler gatewayWebSocketHandler(
            GatewayTopicRouter router,
            ObjectProvider<GatewayReplayCommandProcessor> replayCommandProcessor
    ) {
        return new GatewayWebSocketHandler(router, replayCommandProcessor.getIfAvailable());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public GatewayEventBridge gatewayEventBridge(
            GatewayTopicRouter router,
            ObjectMapper objectMapper,
            ObjectProvider<InstrumentResolver> instrumentResolver
    ) {
        return new GatewayEventBridge(router, objectMapper, instrumentResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public Object gatewayEventBusRegistrar(GatewayEventBridge bridge, EventBus eventBus) {
        bridge.register(eventBus);
        log.info("Gateway event bridge registered with event bus");
        return bridge;
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayHealthIndicator gatewayHealthIndicator(GatewayTopicRouter router) {
        return new GatewayHealthIndicator(router);
    }
}
