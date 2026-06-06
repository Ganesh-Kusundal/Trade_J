package com.tradej.app.config;

import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the gateway profile wires {@link LoadBalancedBrokerGateway} as the
 * primary {@link IBrokerConnection} without starting the full application or
 * calling broker APIs.
 */
@Tag("component")
class GatewayProfileContextComponentTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    GatewayConfiguration.class,
                    SubscriptionConfiguration.class,
                    MinimalDhanBrokerForGateway.class
            )
            .withPropertyValues("trade.broker-type=gateway");

    @Test
    void gatewayProfileExposesPrimaryLoadBalancedConnection() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("loadBalancedBrokerGateway"));
            IBrokerConnection primary = context.getBean(IBrokerConnection.class);
            assertInstanceOf(LoadBalancedBrokerGateway.class, primary);
        });
    }

    @Test
    void gatewayProfileFailsFastWhenNoBrokerNodesConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(GatewayConfiguration.class)
                .withPropertyValues("trade.broker-type=gateway")
                .run(context -> assertTrue(context.getStartupFailure() != null
                        || !context.isRunning()));
    }

    @Test
    void subscriptionRecoveryStackWiredInGatewayMode() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("reconnectListenerRegistry"));
            assertTrue(context.containsBean("subscriptionCoordinator"));
            assertNotNull(context.getBean(ReconnectListenerRegistry.class));
            assertNotNull(context.getBean(SubscriptionCoordinator.class));
        });
    }

    @Configuration
    static class MinimalDhanBrokerForGateway {
        @Bean
        DhanBrokerConnection dhanBrokerConnection() {
            return new DhanBrokerConnection(
                    DhanConnectionSettings.sandboxWithDefaults("gateway-component-test", "unused-token"),
                    new MultiBucketRateLimiter(java.util.Map.of()),
                    new CaffeineIdempotencyCache()
            );
        }
    }
}
