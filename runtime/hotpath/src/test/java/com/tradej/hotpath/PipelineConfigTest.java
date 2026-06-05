package com.tradej.hotpath;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.StrategyError;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.hotpath.PipelineConfig.PipelineComponents;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.StrategyEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PipelineConfigTest {

    @TempDir
    Path tempDir;

    private PositionRiskHandler createRiskHandler() {
        return new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(50_000L, 3, 500_000L, 3),
                Map::of
        );
    }

    private ExecutionHandler createExecutionHandler() {
        EventSourcedOrderRepository oms = new EventSourcedOrderRepository(tempDir.resolve("oms-queue"));
        TradingCircuitBreaker breaker = new TradingCircuitBreaker();
        IBrokerConnection broker = new IBrokerConnection() {
            @Override public OrderCommand orders() { return new OrderCommand() {
                @Override public com.tradej.core.domain.model.Order placeOrder(com.tradej.core.domain.model.OrderRequest request) { return null; }
                @Override public com.tradej.core.domain.model.Order modifyOrder(com.tradej.core.domain.model.ModifyOrderRequest request) { return null; }
                @Override public boolean cancelOrder(String orderId) { return false; }
                @Override public List<String> cancelAllOpenOrders() { return List.of(); }
                @Override public List<String> cancelAndSquareOffIntradayPositions() { return List.of(); }
                @Override public boolean setKillSwitch(boolean enabled) { return false; }
                @Override public com.tradej.core.domain.model.OrderPreview previewOrder(com.tradej.core.domain.model.OrderRequest request) { return null; }
            }; }
            @Override public MarketDataProvider marketData() { return null; }
            @Override public FuturesProvider futures() { return null; }
            @Override public OptionsProvider options() { return null; }
            @Override public OrderQuery orderQuery() { return null; }
            @Override public SliceOrderCommand sliceOrders() { return null; }
            @Override public BracketOrderProvider bracketOrders() { return null; }
            @Override public GttOrderProvider gttOrders() { return null; }
            @Override public PortfolioProvider portfolio() { return null; }
            @Override public MarginProvider margin() { return null; }
            @Override public SessionRiskProvider sessionRisk() { return null; }
            @Override public ConditionalAlertProvider alerts() { return null; }
            @Override public InstrumentResolver instruments() { return null; }
            @Override public WebSocketMultiplexer websocket() { return null; }
            @Override public void connect() {}
            @Override public void disconnect() {}
            @Override public void loadInstrumentCatalog(Path catalogPath) {}
            @Override public <T> java.util.Optional<T> getCapability(Class<T> capabilityClass) { return java.util.Optional.empty(); }
        };
        OrderManagementService omsService = new OrderManagementService(
                broker,
                new com.tradej.core.domain.runtime.RuntimeModeHolder(),
                new LiveTradingClock(),
                oms
        );
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        return new ExecutionHandler(omsService, runtimeModeHolder, new LiveTradingClock(), breaker, new OrderIdentityRegistry(), com.tradej.core.domain.port.DeadLetterQueue.noop());
    }

    private com.tradej.pipeline.runtime.PipelineRuntimeBridge testBridge(
            PositionRiskHandler riskHandler,
            ExecutionHandler executionHandler
    ) {
        return new com.tradej.disruptor.testsupport.TestPipelineGraphBridge(riskHandler, executionHandler);
    }

    private PipelineConfig.PipelineComponents createWithTestBridge(
            PositionRiskHandler riskHandler,
            CandleAggregationService candleService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine
    ) {
        return PipelineConfig.create(
                1,
                riskHandler,
                candleService,
                strategyEngine,
                executionHandler,
                portfolioEngine,
                com.tradej.disruptor.config.StageTimings.NO_OP,
                null,
                com.tradej.core.domain.port.DeadLetterQueue.noop(),
                testBridge(riskHandler, executionHandler)
        );
    }

    private EventMetadataFactory eventMetadataFactory() {
        return new EventMetadataFactory(new LiveTradingClock());
    }

    @Test
    void createReturnsAllComponents() {
        var risk = createRiskHandler();
        var exec = createExecutionHandler();
        PipelineComponents components = createWithTestBridge(
                risk,
                new CandleAggregationService(),
                new StrategyEngine(List.of(), eventMetadataFactory()),
                exec,
                new PortfolioEngine()
        );
        assertNotNull(components);
        assertNotNull(components.eventBus());
        assertNotNull(components.marketDataPipeline());
        assertNotNull(components.orderPipeline());
    }

    @Test
    void createWithNullPortfolioEngine() {
        var risk = createRiskHandler();
        var exec = createExecutionHandler();
        PipelineComponents components = createWithTestBridge(
                risk,
                new CandleAggregationService(),
                new StrategyEngine(List.of(), eventMetadataFactory()),
                exec,
                null
        );
        assertNotNull(components);
        assertNotNull(components.eventBus());
    }

    @Test
    void eventBusCanPublishEvents() {
        var risk = createRiskHandler();
        var exec = createExecutionHandler();
        PipelineComponents components = createWithTestBridge(
                risk,
                new CandleAggregationService(),
                new StrategyEngine(List.of(), eventMetadataFactory()),
                exec,
                null
        );
        var eventBus = components.eventBus();
        assertDoesNotThrow(() -> eventBus.start());
        assertDoesNotThrow(() -> eventBus.publish(
                new StrategyError(eventMetadataFactory().root(), "test-pipeline", "test", "unit test")
        ));
        assertDoesNotThrow(() -> eventBus.stop());
    }

    @Test
    void nullPositionRiskHandlerThrows() {
        assertThrows(NullPointerException.class,
                () -> PipelineConfig.create(null, new CandleAggregationService(),
                        new StrategyEngine(List.of(), eventMetadataFactory()), createExecutionHandler(), null));
    }

    @Test
    void nullCandleAggregationServiceThrows() {
        assertThrows(NullPointerException.class,
                () -> PipelineConfig.create(createRiskHandler(), null,
                        new StrategyEngine(List.of(), eventMetadataFactory()), createExecutionHandler(), null));
    }

    @Test
    void nullStrategyEngineThrows() {
        assertThrows(NullPointerException.class,
                () -> PipelineConfig.create(createRiskHandler(), new CandleAggregationService(),
                        null, createExecutionHandler(), null));
    }

    @Test
    void nullExecutionHandlerThrows() {
        assertThrows(NullPointerException.class,
                () -> PipelineConfig.create(createRiskHandler(), new CandleAggregationService(),
                        new StrategyEngine(List.of(), eventMetadataFactory()), null, null));
    }
}
