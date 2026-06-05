package com.tradej.app.integration;

import com.tradej.app.TradingApplication;
import com.tradej.app.startup.BrokerStartupOrchestrator;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full application-context smoke test for the gateway profile.
 *
 * <p>Boots {@code @SpringBootTest} with {@code trade.broker-type=gateway},
 * temp instrument CSV stubs, and {@code trade.runtime.mode=REPLAY} to
 * verify that the entire Spring context wires correctly without making
 * real broker API calls.
 *
 * <p>The {@link BrokerStartupOrchestrator} is mocked to prevent the
 * ApplicationRunner from attempting real broker operations with stub tokens.
 * This test verifies Spring wiring, not startup behavior.
 */
@Tag("component")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        classes = TradingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "trade.broker-type=gateway",
                "trade.runtime.mode=REPLAY",
                // Dhan (sandbox stub)
                "trade.broker.clientId=gateway-smoke-test",
                "trade.broker.access-token=smoke-test-token",
                "trade.broker.environment=SANDBOX",
                "trade.broker.auth-mode=STATIC",
                // ICICI (minimal stub)
                "trade.icici.appKey=smoke-app-key",
                "trade.icici.secretKey=smoke-secret-key",
                "trade.icici.auth-mode=STATIC",
                // Upstox (analytics-only stub)
                "trade.upstox.clientId=smoke-upstox-id",
                "trade.upstox.clientSecret=smoke-upstox-secret",
                "trade.upstox.accessToken=smoke-access-token",
                "trade.upstox.analytics-only=true",
                "trade.upstox.analytics-token=smoke-analytics-token",
                // Storage stubs
                "trade.storage.chroniclePath=build/smoke-chronicle",
                "trade.storage.duckdbPath=build/smoke-duckdb.duckdb",
                // Subscriptions (one static subscription for gateway)
                "trade.subscriptions[0].symbol=SBIN",
                "trade.subscriptions[0].exchangeSegment=NSE_EQ",
                "trade.subscriptions[0].feedMode=TICKER"
        }
)
class GatewayReplaySmokeTest {

    // Temp directories created before class loading so they exist when
    // the Spring context boots and BrokerStartupOrchestrator.loadCatalog() runs.
    private static final Path SMOKE_DIR;
    private static final Path CATALOG_DIR;

    static {
        try {
            SMOKE_DIR = Files.createTempDirectory("gateway-smoke");
            CATALOG_DIR = SMOKE_DIR.resolve("instruments");
            Files.createDirectory(CATALOG_DIR);
            Files.writeString(CATALOG_DIR.resolve("instruments.csv"), """
                    symbol,exchange,exchangeSegment,securityId
                    SBIN,NSE,NSE_EQ,3045
                    """);
            // Valid empty token states so DhanTokenStateStore/BreezeTokenStateStore don't fail
            Files.writeString(SMOKE_DIR.resolve("dhan-token-state.json"), "{}");
            Files.writeString(SMOKE_DIR.resolve("icici-token-state.json"), "{}");
            Files.writeString(SMOKE_DIR.resolve("upstox-token-state.json"), "{}");
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create temp smoke-test files", ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("trade.instruments.cache-directory", CATALOG_DIR::toString);
        registry.add("trade.broker.token-state-file", () -> SMOKE_DIR.resolve("dhan-token-state.json").toString());
        registry.add("trade.icici.token-state-file", () -> SMOKE_DIR.resolve("icici-token-state.json").toString());
    }

    @MockitoBean
    private BrokerStartupOrchestrator brokerStartupOrchestrator;

    @Autowired
    private IBrokerConnection brokerConnection;

    @Autowired
    private RuntimeModeHolder runtimeModeHolder;

    @Test
    void contextBootsWithGatewayProfile() {
        assertInstanceOf(LoadBalancedBrokerGateway.class, brokerConnection);
    }

    @Test
    void gatewayConnectionCountIsAtLeastOne() {
        assertInstanceOf(LoadBalancedBrokerGateway.class, brokerConnection);
        LoadBalancedBrokerGateway gateway = (LoadBalancedBrokerGateway) brokerConnection;
        assertTrue(gateway.connectionCount() >= 1,
                "Gateway should aggregate at least one broker node");
    }

    @Test
    void replayModeAppliedAtStartup() {
        assertEquals(RuntimeMode.REPLAY, runtimeModeHolder.mode(),
                "RuntimeModeHolder should reflect the configured REPLAY mode");
    }
}
