package com.tradej.app.integration;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.app.pipeline.ReplayOrchestrator;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.strategy.service.StrategyEngine;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Abstract base for {@link AdminController} integration tests.
 * <p>
 * Provides all 14 mocked dependencies and the shared {@link TestRestTemplate}.
 * Subclasses add endpoint-specific test methods and inherit the Spring Boot
 * test setup and {@link DirtiesContext} isolation.
 */
@Tag("integration")
@Tag("api")
@SpringBootTest(
        classes = AdminTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
abstract class AdminTestBase {

    @Autowired
    protected TestRestTemplate rest;

    // ── All AdminController constructor dependencies ──

    @MockitoBean
    protected IBrokerConnection brokerConnection;

    @MockitoBean
    protected TradingCircuitBreaker tradingCircuitBreaker;

    @MockitoBean
    protected OrderReconciler orderReconciler;

    @MockitoBean
    protected EventBus eventBus;

    @MockitoBean
    protected DisruptorBusMetrics disruptorBusMetrics;

    @MockitoBean
    protected RuntimeHealthState runtimeHealthState;

    @MockitoBean
    protected ExecutionHandler executionHandler;

    @MockitoBean
    protected MarketDataPipeline marketDataPipeline;

    @MockitoBean
    protected OrderPipeline orderPipeline;

    @MockitoBean
    protected StrategyEngine strategyEngine;

    @MockitoBean(name = "localHistoricalRangeService")
    @Qualifier("localHistoricalRangeService")
    protected HistoricalRangeService historicalRangeService;

    @MockitoBean
    protected RuntimeModeHolder runtimeModeHolder;

    @MockitoBean
    protected ReplayOrchestrator replayOrchestrator;

    @MockitoBean
    protected WebSocketMultiplexer webSocketMultiplexer;
}
