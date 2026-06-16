package com.tradej.app.integration;

import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.broker.dhan.depth.DhanMarketDepthProvider;
import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import com.tradej.replay.engine.ReplayOrchestrator;
import com.tradej.app.TradingApplication;
import com.tradej.app.health.PlatformHealthIndicator;
import com.tradej.app.startup.BrokerStartupOrchestrator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test that boots the full Spring application context with the dev profile
 * and verifies that all critical beans for the instrument catalog, WebSocket gateway,
 * and depth-20 pipeline are present.
 *
 * <p>The {@link BrokerStartupOrchestrator} is mocked to prevent actual broker
 * connection attempts during the test.
 *
 * <p>Uses {@link TempDir} for all runtime paths to avoid AccessDeniedException
 * from hardcoded build/ directories that may not be writable.
 */
@Tag("component")
@SpringBootTest(
        classes = TradingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "trade.broker-type=dhan",
                "trade.runtime.mode=LIVE",
                "tradej.gateway.enabled=true",
                "trade.broker.clientId=smoke-test",
                "trade.broker.access-token=smoke-token",
                "trade.broker.environment=SANDBOX",
                "trade.broker.auth-mode=STATIC",
                "trade.subscriptions[0].symbol=NIFTY",
                "trade.subscriptions[0].exchangeSegment=IDX_I",
                "trade.subscriptions[0].feedMode=TICKER"
        }
)
class StartupSmokeComponentTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("trade.broker.token-state-file", () -> tempDir.resolve("token-state.json").toString());
        registry.add("trade.storage.chroniclePath", () -> tempDir.resolve("chronicle").toString());
        registry.add("trade.storage.duckdbPath", () -> tempDir.resolve("trade.duckdb").toString());
        registry.add("trade.storage.historicalWarehousePath", () -> tempDir.resolve("historical.duckdb").toString());
        registry.add("trade.historical-equity.root-path", () -> tempDir.resolve("historical-equity").toString());
        registry.add("trade.instruments.cache-directory", () -> tempDir.resolve("instruments").toString());
    }

    @MockitoBean
    private BrokerStartupOrchestrator brokerStartupOrchestrator;

    @MockitoBean
    private BrokerRegistry brokerRegistry;

    @MockitoBean
    private PlatformHealthIndicator platformHealthIndicator;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithAllCriticalBeans() {
        assertNotNull(context);
    }

    @Test
    void executionHandlerBeanExists() {
        assertTrue(context.containsBean("executionHandler"),
                "ExecutionHandler bean should be defined");
        assertNotNull(context.getBean(ExecutionHandler.class));
    }

    @Test
    void replayOrchestratorBeanExists() {
        assertTrue(context.containsBean("replayOrchestrator"),
                "ReplayOrchestrator bean should be defined");
        assertNotNull(context.getBean(ReplayOrchestrator.class));
    }

    @Test
    void gatewayTopicRouterBeanExists() {
        assertTrue(context.containsBean("gatewayTopicRouter"),
                "GatewayTopicRouter bean should be defined");
        assertNotNull(context.getBean(GatewayTopicRouter.class));
    }

    @Test
    void gatewayWebSocketHandlerBeanExists() {
        assertTrue(context.containsBean("gatewayWebSocketHandler"),
                "GatewayWebSocketHandler bean should be defined");
        assertNotNull(context.getBean(GatewayWebSocketHandler.class));
    }

    @Test
    void orderBookEngineBeanExists() {
        assertTrue(context.containsBean("orderBookEngine"),
                "OrderBookEngine bean should be defined");
        assertNotNull(context.getBean(OrderBookEngine.class));
    }

    @Test
    void dhanMarketDepthProviderBeanExists() {
        assertTrue(context.containsBean("dhanMarketDepthProvider"),
                "DhanMarketDepthProvider bean should be defined");
        assertNotNull(context.getBean(DhanMarketDepthProvider.class));
    }
}
