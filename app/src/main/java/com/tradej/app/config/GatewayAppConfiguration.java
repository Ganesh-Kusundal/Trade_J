package com.tradej.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.replay.engine.CandleReplaySession;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableScheduling
public class GatewayAppConfiguration {

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
