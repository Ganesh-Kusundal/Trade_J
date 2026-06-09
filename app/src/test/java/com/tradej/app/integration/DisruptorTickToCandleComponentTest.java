package com.tradej.app.integration;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import java.util.Optional;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("component")
class DisruptorTickToCandleComponentTest {
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
    void publishesCandleEventsFromSyntheticTicks() throws Exception {
        PositionRiskHandler riskHandler = new PositionRiskHandler(RiskLimits.withOpenPositionQuantity(10, 10, 10_000_000L, 10), NetPositionProvider.empty());
        CandleAggregationService candleService = new CandleAggregationService();
        TradingClock clock = new LiveTradingClock();
        EventMetadataFactory metadataFactory = new EventMetadataFactory(clock);

        omsRepository = new EventSourcedOrderRepository(Files.createTempDirectory("disruptor-oms"));
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        executionHandler = new ExecutionHandler(
                new OrderManagementService(null, runtimeModeHolder, clock, omsRepository),
                runtimeModeHolder,
                clock,
                new TradingCircuitBreaker(),
                new OrderIdentityRegistry(),
                com.tradej.core.domain.port.DeadLetterQueue.noop()
        );

        var bridge = new com.tradej.disruptor.testsupport.TestPipelineGraphBridge(
                new com.tradej.pipeline.graph.PipelineGraph(
                        "tick-candle-test",
                        "Tick Candle Test",
                        1,
                        java.util.List.of(
                                new com.tradej.pipeline.graph.PipelineNodeDef(
                                        "candle-1", com.tradej.pipeline.runtime.PipelineNodeTypes.CANDLE, "Candle", java.util.Map.of())
                        ),
                        java.util.List.of()
                ),
                def -> new com.tradej.strategy.node.CandleNode(candleService)
        );

        eventBus = new com.tradej.disruptor.config.DisruptorPipelineBuilder()
                .positionRiskHandler(riskHandler)
                .candleAggregationService(candleService)
                .executionHandler(executionHandler)
                .stageTimings(com.tradej.disruptor.config.StageTimings.NO_OP)
                .deadLetterQueue(com.tradej.core.domain.port.DeadLetterQueue.noop())
                .pipelineRuntimeBridge(bridge)
                .buildBus();
        AtomicInteger developingCount = new AtomicInteger();
        CountDownLatch closedLatch = new CountDownLatch(1);
        eventBus.subscribe(CandleDeveloping.class, event -> developingCount.incrementAndGet());
        eventBus.subscribe(CandleClosed.class, event -> closedLatch.countDown());
        eventBus.start();

        long t0 = 1_710_000_000_000L;
        eventBus.publish(new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 75_000L, 10L, 10L, t0, Optional.empty(), 0L, 0L));
        eventBus.publish(new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 75_500L, 5L, 15L, t0 + 60_000L, Optional.empty(), 0L, 0L));
        eventBus.publish(new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 76_000L, 7L, 22L, t0 + 300_000L, Optional.empty(), 0L, 0L));

        assertTrue(closedLatch.await(5, TimeUnit.SECONDS), "Disruptor pipeline should emit CandleClosed for bucket rollover.");
        assertTrue(developingCount.get() >= 2, "Expected at least two CandleDeveloping events.");
    }
}
