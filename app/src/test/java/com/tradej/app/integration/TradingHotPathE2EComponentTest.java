package com.tradej.app.integration;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.oms.LifecycleState;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremental end-to-end verification of the trading hot path without mocks:
 * signal → risk → execution → OMS → DuckDB → historical query → partial-fill replay.
 */
@Tag("component")
class TradingHotPathE2EComponentTest {

    private Path dbPath;
    private EventSourcedOrderRepository omsRepository;
    private ExecutionHandler executionHandler;
    private DuckDbEventStore eventStore;
    private HistoricalRangeService historicalRangeService;

    @AfterEach
    void tearDown() throws Exception {
        if (executionHandler != null) {
            executionHandler.stop();
        }
        if (omsRepository != null) {
            omsRepository.close();
        }
        if (historicalRangeService != null) {
            historicalRangeService.close();
        }
        if (eventStore != null) {
            eventStore.close();
        }
        if (dbPath != null) {
            Files.deleteIfExists(dbPath);
        }
    }

    @Test
    void fullHotPathCyclePersistsAndReplaysByVirtualTime() throws Exception {
        dbPath = Files.createTempFile("hotpath-e2e-", ".duckdb");
        Files.deleteIfExists(dbPath);
        AtomicLong ingestClock = new AtomicLong(1_800_000_000_000L);
        eventStore = new DuckDbEventStore(dbPath, () -> ingestClock.addAndGet(60_000L));
        historicalRangeService = new HistoricalRangeService(dbPath);

        PortfolioEngine portfolioEngine = new PortfolioEngine(10_000_000L, 50_000_000L);
        PositionRiskHandler riskHandler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(10_000_000L, 10, 10_000_000L, 100),
                NetPositionProvider.empty(),
                portfolioEngine
        );

        RuntimeModeHolder runtimeModeHolder = new RuntimeModeHolder();
        runtimeModeHolder.setMode(RuntimeMode.REPLAY);

        omsRepository = new EventSourcedOrderRepository(Files.createTempDirectory("hotpath-e2e-oms"));
        OrderIdentityRegistry identityRegistry = new OrderIdentityRegistry();
        OrderManagementService orderManagementService = new OrderManagementService(
                null,
                runtimeModeHolder,
                new LiveTradingClock(),
                omsRepository
        );
        executionHandler = new ExecutionHandler(
                orderManagementService,
                runtimeModeHolder,
                new LiveTradingClock(),
                new TradingCircuitBreaker(),
                identityRegistry,
                DeadLetterQueue.noop()
        );
        executionHandler.start();

        List<DomainEvent> captured = new ArrayList<>();
        OrderPipeline orderPipeline = new OrderPipeline(
                event -> executionHandler.onDomainEvent(event, captured::add));

        CountDownLatch acceptedLatch = new CountDownLatch(1);
        var consumer = new java.util.function.Consumer<DomainEvent>() {
            @Override
            public void accept(DomainEvent event) {
                captured.add(event);
                if (event instanceof SignalGenerated generated) {
                    riskHandler.onDomainEvent(generated, this);
                } else if (event instanceof SignalPendingExecution pending) {
                    executionHandler.onDomainEvent(pending, this);
                } else if (event instanceof OrderAccepted accepted) {
                    acceptedLatch.countDown();
                }
            }
        };

        long signalTimeMs = 5_000L;
        consumer.accept(new SignalGenerated(
                new EventMetadata(UUID.randomUUID().toString(), signalTimeMs, 1L, 1L, "corr-e2e", 1),
                "sig-e2e",
                "SBIN",
                "5m",
                Side.BUY,
                75_000L,
                70_000L,
                80_000L,
                "e2e",
                Map.of("strategyName", "e2e", "quantity", 100L)
        ));

        assertTrue(acceptedLatch.await(8, TimeUnit.SECONDS));
        assertFalse(captured.stream().anyMatch(e -> e instanceof com.tradej.core.domain.event.SignalSuppressed));

        OrderAccepted accepted = captured.stream()
                .filter(OrderAccepted.class::isInstance)
                .map(OrderAccepted.class::cast)
                .findFirst()
                .orElseThrow();
        String internalOrderId = omsRepository.knownOrderIds().getFirst();

        for (DomainEvent event : captured) {
            if (event instanceof OrderAccepted || event instanceof SignalPendingExecution) {
                eventStore.onEvent(event);
            }
        }

        Order brokerOrder = new Order(
                accepted.order().orderId(),
                accepted.order().correlationId(),
                "SBIN",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                ProductType.INTRADAY,
                OrderType.MARKET,
                OrderStatus.PART_TRADED,
                100L,
                40L,
                150_00L,
                0L,
                0L,
                ""
        );
        long partialTimeMs = 6_000L;
        OrderPartiallyFilled partial = new OrderPartiallyFilled(
                new EventMetadata(UUID.randomUUID().toString(), partialTimeMs, 2L, 2L, "corr-e2e", 1),
                brokerOrder,
                List.of(new Trade("TR-E2E", brokerOrder.orderId(), "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 40L, 150_00L, 1L))
        );
        orderPipeline.onOrderPartiallyFilled(partial);
        Thread.sleep(500L);

        OrderProjection projection = omsRepository.rebuild(internalOrderId);
        assertEquals(LifecycleState.PARTIALLY_FILLED, projection.status());
        assertEquals(40L, projection.filledQuantity());

        eventStore.onEvent(partial);

        List<HistoricalRangeService.HistoricalFillEvent> replayed =
                historicalRangeService.queryFillEvents("SBIN", 5_500L, 6_500L, 10);
        assertEquals(1, replayed.size());
        assertEquals("PARTIALLY_FILLED", replayed.getFirst().eventType());
    }
}
