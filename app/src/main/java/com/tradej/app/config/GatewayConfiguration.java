package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Multi-broker gateway profile: aggregates all available broker connections.
 */
@Configuration
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "gateway")
public class GatewayConfiguration {

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
