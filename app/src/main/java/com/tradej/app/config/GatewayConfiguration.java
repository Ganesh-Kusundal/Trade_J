package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-broker gateway profile: aggregates Dhan, ICICI, and Upstox connections.
 */
@Configuration
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "gateway")
public class GatewayConfiguration {

    @Bean(name = "loadBalancedBrokerGateway")
    @Primary
    IBrokerConnection loadBalancedBrokerGateway(
            ObjectProvider<DhanBrokerConnection> dhanConnection,
            ObjectProvider<IciciBrokerConnection> iciciConnection,
            ObjectProvider<UpstoxBrokerConnection> upstoxConnection,
            ReconnectListenerRegistry reconnectListenerRegistry
    ) {
        List<IBrokerConnection> nodes = new ArrayList<>();
        dhanConnection.ifAvailable(nodes::add);
        iciciConnection.ifAvailable(nodes::add);
        upstoxConnection.ifAvailable(nodes::add);
        if (nodes.isEmpty()) {
            throw new IllegalStateException(
                    "trade.broker-type=gateway requires at least one broker adapter on the classpath "
                            + "(enable dhan/icici/upstox configuration properties)");
        }
        return new LoadBalancedBrokerGateway(nodes, reconnectListenerRegistry);
    }
}
