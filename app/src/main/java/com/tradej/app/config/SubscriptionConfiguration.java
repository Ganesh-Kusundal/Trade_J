package com.tradej.app.config;

import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.execution.subscription.SubscriptionManager;
import com.tradej.execution.subscription.SubscriptionRecoveryManager;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the subscription recovery stack.
 *
 * <p>{@link ReconnectListenerRegistry} is a shared singleton that all broker
 * WebSocket multiplexers notify on reconnect. {@link SubscriptionRecoveryManager}
 * is registered as a listener to re-apply desired subscriptions after any reconnect.
 *
 * <p>The {@link SubscriptionCoordinator} and {@link SubscriptionManager} resolve
 * their {@link WebSocketMultiplexer} via {@link IBrokerConnection#websocket()},
 * which returns the {@code FailoverWebSocketMultiplexer} in gateway mode and the
 * raw broker multiplexer in single-broker mode.
 */
@Configuration
public class SubscriptionConfiguration {

    private static final int DEFAULT_BATCH_SIZE = 50;

    @Bean
    ReconnectListenerRegistry reconnectListenerRegistry() {
        return new ReconnectListenerRegistry();
    }

    @Bean
    SubscriptionCoordinator subscriptionCoordinator(
            IBrokerConnection brokerConnection
    ) {
        return new SubscriptionCoordinator(brokerConnection.websocket(), DEFAULT_BATCH_SIZE);
    }

    @Bean
    SubscriptionManager subscriptionManager(
            SubscriptionCoordinator subscriptionCoordinator,
            IBrokerConnection brokerConnection
    ) {
        return new SubscriptionManager(subscriptionCoordinator, brokerConnection.websocket());
    }

    @Bean
    SubscriptionRecoveryManager subscriptionRecoveryManager(
            SubscriptionManager subscriptionManager,
            ReconnectListenerRegistry reconnectListenerRegistry
    ) {
        SubscriptionRecoveryManager recovery = new SubscriptionRecoveryManager(subscriptionManager);
        reconnectListenerRegistry.addListener(recovery::recoverAfterReconnect);
        return recovery;
    }
}
