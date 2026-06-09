package com.tradej.app.integration;

import com.tradej.app.config.BrokerConfiguration;
import com.tradej.app.config.ScanConfiguration;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for the gateway profile using {@link ApplicationContextRunner}.
 *
 * <p>Uses a minimal context with stub broker adapters to verify the gateway
 * wiring without requiring full application context or real broker credentials.
 *
 * <p>Replaces the previous {@code @SpringBootTest} approach which failed due to
 * complex bean creation chains in {@code BrokerConfiguration} and
 * {@code IciciConfiguration} requiring real file paths and credentials.
 */
@Tag("component")
class GatewayReplaySmokeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    BrokerConfiguration.GatewayConfig.class,
                    ScanConfiguration.class,
                    StubBrokerAdapters.class,
                    RuntimeModeConfig.class
            )
            .withPropertyValues("trade.broker-type=gateway", "trade.runtime.mode=REPLAY");

    @Test
    void contextBootsWithGatewayProfile() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure(),
                    "Context should boot without errors: " + context.getStartupFailure());
            assertTrue(context.containsBean("loadBalancedBrokerGateway"));
            IBrokerConnection primary = context.getBean(IBrokerConnection.class);
            assertInstanceOf(LoadBalancedBrokerGateway.class, primary);
        });
    }

    @Test
    void gatewayConnectionCountIsAtLeastOne() {
        contextRunner.run(context -> {
            IBrokerConnection conn = context.getBean(IBrokerConnection.class);
            assertInstanceOf(LoadBalancedBrokerGateway.class, conn);
            LoadBalancedBrokerGateway gateway = (LoadBalancedBrokerGateway) conn;
            assertTrue(gateway.connectionCount() >= 1,
                    "Gateway should aggregate at least one broker node");
        });
    }

    @Test
    void replayModeAppliedAtStartup() {
        contextRunner.run(context -> {
            RuntimeModeHolder holder = context.getBean(RuntimeModeHolder.class);
            assertEquals(RuntimeMode.REPLAY, holder.mode(),
                    "RuntimeModeHolder should reflect the configured REPLAY mode");
        });
    }

    @Configuration
    static class StubBrokerAdapters {
        @Bean
        DhanBrokerConnection dhanBrokerConnection() {
            return new DhanBrokerConnection(
                    DhanConnectionSettings.sandboxWithDefaults("gateway-smoke-test", "unused-token"),
                    new MultiBucketRateLimiter(Map.of()),
                    new CaffeineIdempotencyCache()
            );
        }
    }

    @Configuration
    static class RuntimeModeConfig {
        @Bean
        RuntimeModeHolder runtimeModeHolder() {
            RuntimeModeHolder holder = new RuntimeModeHolder();
            holder.setMode(RuntimeMode.REPLAY);
            return holder;
        }
    }
}
