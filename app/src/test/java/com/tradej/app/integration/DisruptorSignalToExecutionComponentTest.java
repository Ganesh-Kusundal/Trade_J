package com.tradej.app.integration;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full {@link DisruptorEventBus} path: {@link SignalGenerated} → risk → {@link SignalPendingExecution}
 * → {@link ExecutionHandler} (simulated in REPLAY mode).
 */
@Tag("component")
class DisruptorSignalToExecutionComponentTest {

    private DisruptorEventBus eventBus;
    private ExecutionHandler executionHandler;
    private EventSourcedOrderRepository omsRepository;

    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.stop();
        }
        if (executionHandler != null) {
            executionHandler.stop();
        }
        if (omsRepository != null) {
            omsRepository.close();
        }
    }

    @Test
    void signalGeneratedReachesSimulatedOrderAcceptance() throws Exception {
        PortfolioEngine portfolioEngine = new PortfolioEngine(10_000_000L, 50_000_000L);
        PositionRiskHandler riskHandler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(10_000_000L, 10, 10_000_000L, 100),
                NetPositionProvider.empty(),
                portfolioEngine
        );
        CandleAggregationService candleService = new CandleAggregationService(List.of("1m"));

        RuntimeModeHolder runtimeModeHolder = new RuntimeModeHolder();
        runtimeModeHolder.setMode(RuntimeMode.REPLAY);

        omsRepository = new EventSourcedOrderRepository(Files.createTempDirectory("signal-oms"));
        executionHandler = new ExecutionHandler(
                new OrderManagementService(null, runtimeModeHolder, new LiveTradingClock(), omsRepository),
                runtimeModeHolder,
                new LiveTradingClock(),
                new TradingCircuitBreaker(),
                new OrderIdentityRegistry(),
                DeadLetterQueue.noop(),
                com.tradej.execution.service.ExecutionConfig.DEFAULTS.withDownstream(e -> {
                    if (eventBus != null) {
                        eventBus.publish(e);
                    }
                })
        );

        var bridge = new com.tradej.disruptor.testsupport.TestPipelineGraphBridge(riskHandler, executionHandler);

        eventBus = new com.tradej.disruptor.config.DisruptorPipelineBuilder()
                .positionRiskHandler(riskHandler)
                .candleAggregationService(candleService)
                .executionHandler(executionHandler)
                .portfolioEngine(portfolioEngine)
                .stageTimings(com.tradej.disruptor.config.StageTimings.NO_OP)
                .deadLetterQueue(DeadLetterQueue.noop())
                .pipelineRuntimeBridge(bridge)
                .buildBus();

        List<DomainEvent> captured = new ArrayList<>();
        CountDownLatch acceptedLatch = new CountDownLatch(1);
        eventBus.subscribe(SignalPendingExecution.class, captured::add);
        eventBus.subscribe(SignalSuppressed.class, captured::add);
        eventBus.subscribe(OrderAccepted.class, e -> {
            captured.add(e);
            acceptedLatch.countDown();
        });
        eventBus.start();

        SignalGenerated signal = new SignalGenerated(
                EventMetadata.root(),
                "sig-pipeline-1",
                "SBIN",
                "5m",
                com.tradej.core.domain.value.Side.BUY,
                75_000L,
                70_000L,
                80_000L,
                "component-test",
                Map.of("strategyName", "pipeline-test", "quantity", 5L)
        );
        eventBus.publish(signal);

        assertTrue(acceptedLatch.await(8, TimeUnit.SECONDS), "REPLAY mode should simulate order acceptance");
        assertFalse(captured.stream().anyMatch(SignalSuppressed.class::isInstance),
                "Signal must not be suppressed on valid path");
        assertTrue(captured.stream().anyMatch(SignalPendingExecution.class::isInstance),
                "Risk stage must forward SignalPendingExecution");
        assertTrue(captured.stream().anyMatch(OrderAccepted.class::isInstance),
                "Execution stage must emit OrderAccepted");
    }
}
