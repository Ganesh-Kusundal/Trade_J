package com.tradej.app.integration;

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
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NR-03: {@link OrderPipeline} must route broker {@link OrderPartiallyFilled} into {@link ExecutionHandler}
 * so OMS {@code filledQuantity} advances on the default hot path.
 */
@Tag("component")
class OrderPartiallyFilledHotPathComponentTest {

    private EventSourcedOrderRepository omsRepository;
    private ExecutionHandler executionHandler;
    private OrderIdentityRegistry identityRegistry;

    @AfterEach
    void tearDown() {
        if (executionHandler != null) {
            executionHandler.stop();
        }
        if (omsRepository != null) {
            omsRepository.close();
        }
    }

    @Test
    void orderPipelinePartialFillUpdatesOmsProjection() throws Exception {
        PortfolioEngine portfolioEngine = new PortfolioEngine(10_000_000L, 50_000_000L);
        PositionRiskHandler riskHandler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(10_000_000L, 10, 10_000_000L, 100),
                NetPositionProvider.empty(),
                portfolioEngine
        );

        RuntimeModeHolder runtimeModeHolder = new RuntimeModeHolder();
        runtimeModeHolder.setMode(RuntimeMode.REPLAY);

        omsRepository = new EventSourcedOrderRepository(Files.createTempDirectory("partial-fill-oms"));
        identityRegistry = new OrderIdentityRegistry();
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

        List<com.tradej.core.domain.event.DomainEvent> captured = new ArrayList<>();
        OrderPipeline orderPipeline = new OrderPipeline(
                event -> executionHandler.onDomainEvent(event, captured::add));

        CountDownLatch acceptedLatch = new CountDownLatch(1);
        var signalConsumer = new java.util.function.Consumer<com.tradej.core.domain.event.DomainEvent>() {
            @Override
            public void accept(com.tradej.core.domain.event.DomainEvent event) {
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

        SignalGenerated signal = new SignalGenerated(
                EventMetadata.root(),
                "sig-partial-1",
                "SBIN",
                "5m",
                Side.BUY,
                75_000L,
                70_000L,
                80_000L,
                "partial-fill-test",
                Map.of("strategyName", "partial-fill", "quantity", 100L)
        );
        signalConsumer.accept(signal);

        assertTrue(acceptedLatch.await(8, TimeUnit.SECONDS), "Order must be accepted before partial fill");

        String internalOrderId = omsRepository.knownOrderIds().getFirst();
        OrderAccepted accepted = captured.stream()
                .filter(OrderAccepted.class::isInstance)
                .map(OrderAccepted.class::cast)
                .findFirst()
                .orElseThrow();
        String brokerOrderId = accepted.order().orderId();

        Order brokerOrder = new Order(
                brokerOrderId,
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
        List<Trade> fills = List.of(
                new Trade("TR-1", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 40L, 150_00L, 1L)
        );
        orderPipeline.onOrderPartiallyFilled(
                new OrderPartiallyFilled(EventMetadata.correlated(accepted.order().correlationId(), 2L), brokerOrder, fills)
        );

        Thread.sleep(500L);

        OrderProjection projection = omsRepository.rebuild(internalOrderId);
        assertEquals(LifecycleState.PARTIALLY_FILLED, projection.status());
        assertEquals(40L, projection.filledQuantity());
    }
}
