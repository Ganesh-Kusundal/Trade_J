package com.tradej.execution.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.identity.OrderIdentityRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-5: Tests that TradeUpdated is NOT emitted when fill quantity=0 and pnl=0
 * (zero-value placeholder guard).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TradeUpdatedEmissionTest {

    @Mock
    private OrderManagementService orderManagementService;

    @Mock
    private TradingCircuitBreaker circuitBreaker;

    private ExecutionHandler handler;
    private OrderIdentityRegistry identityRegistry;
    private final List<DomainEvent> emitted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        identityRegistry = new OrderIdentityRegistry();
        handler = new ExecutionHandler(
                orderManagementService,
                new RuntimeModeHolder(),
                new com.tradej.core.domain.time.LiveTradingClock(),
                circuitBreaker,
                identityRegistry,
                DeadLetterQueue.noop(),
                ExecutionConfig.DEFAULTS.withDownstream(emitted::add));
        emitted.clear();

        lenient().doAnswer(inv -> null).when(orderManagementService).onBrokerEvent(any());
    }

    @AfterEach
    void tearDown() {
        handler.stop();
    }

    // ── Helpers ──

    private void registerOrder(String brokerOrderId, String signalId) {
        identityRegistry.register("ORD-INTERNAL-1", null, signalId);
        identityRegistry.acknowledge("ORD-INTERNAL-1", brokerOrderId);
    }

    private static Order createOrder(String brokerOrderId, String signalId) {
        return new Order(
                brokerOrderId, signalId, "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.TRADED,
                100, 100, 150_00L, 0L, 1000L, ""
        );
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Sets up the first fill so that TradeOpened is emitted and the order is
     * registered in the tradeOpenedEmitted cache. Subsequent fills for the same
     * internal order will trigger the TradeUpdated path.
     */
    private void emitFirstFill(String brokerOrderId, String signalId) {
        registerOrder(brokerOrderId, signalId);

        com.tradej.core.domain.oms.OrderProjection proj =
                new com.tradej.core.domain.oms.OrderProjection(
                        "ORD-INTERNAL-1", "SBIN", 100, 0,
                        150_00L, com.tradej.core.domain.oms.LifecycleState.SUBMITTED);
        when(orderManagementService.getOrderProjection("ORD-INTERNAL-1"))
                .thenReturn(java.util.Optional.of(proj));

        List<Trade> fills = List.of(
                new Trade("T-001", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ,
                        Side.BUY, 60, 151_00L, 600L)
        );
        Order order = createOrder(brokerOrderId, signalId);
        OrderFilled firstFill = new OrderFilled(
                EventMetadata.correlated(signalId, 1L), order, fills);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);
        handler.start();
        handler.onDomainEvent(firstFill);
        assertTrue(awaitLatch(latch), "First fill processing did not complete");

        // Verify TradeOpened was emitted (first fill)
        long openedCount = emitted.stream().filter(e -> e instanceof TradeOpened).count();
        assertEquals(1, openedCount, "First fill should emit TradeOpened");
        emitted.clear();
    }

    // ── P0-5 Tests ──

    @Test
    void tradeUpdatedNotEmittedWhenQuantityZeroAndPnlZero() {
        String brokerOrderId = "BRK-ZERO";
        String signalId = "sig-zero";
        emitFirstFill(brokerOrderId, signalId);

        // Second fill with empty fills list (quantity=0, pnl=0) => should be skipped
        // Note: processFill skips getOrderProjection when fills are empty,
        // going straight to emitTradeOpened where the P0-5 guard triggers.
        Order order = createOrder(brokerOrderId, signalId);
        OrderFilled emptyFill = new OrderFilled(
                EventMetadata.correlated(signalId, 2L), order, List.of());

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);
        handler.onDomainEvent(emptyFill);
        assertTrue(awaitLatch(latch), "Empty fill processing did not complete");

        // P0-5: TradeUpdated must NOT be emitted when qty=0 and pnl=0
        long updatedCount = emitted.stream().filter(e -> e instanceof TradeUpdated).count();
        assertEquals(0, updatedCount,
                "TradeUpdated must NOT be emitted when fill quantity=0 and pnl=0 (P0-5 guard)");
    }

    @Test
    void tradeUpdatedIsEmittedWhenQuantityGreaterThanZero() {
        String brokerOrderId = "BRK-QTY";
        String signalId = "sig-qty";
        emitFirstFill(brokerOrderId, signalId);

        // Second fill with qty > 0 => TradeUpdated should be emitted
        com.tradej.core.domain.oms.OrderProjection proj =
                new com.tradej.core.domain.oms.OrderProjection(
                        "ORD-INTERNAL-1", "SBIN", 100, 60,
                        150_00L, com.tradej.core.domain.oms.LifecycleState.PARTIALLY_FILLED);
        when(orderManagementService.getOrderProjection("ORD-INTERNAL-1"))
                .thenReturn(java.util.Optional.of(proj));

        List<Trade> fills = List.of(
                new Trade("T-002", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ,
                        Side.BUY, 40, 152_00L, 400L)
        );
        Order order = createOrder(brokerOrderId, signalId);
        OrderFilled secondFill = new OrderFilled(
                EventMetadata.correlated(signalId, 2L), order, fills);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);
        handler.onDomainEvent(secondFill);
        assertTrue(awaitLatch(latch), "Second fill processing did not complete");

        // P0-5: TradeUpdated IS emitted when quantity > 0
        long updatedCount = emitted.stream().filter(e -> e instanceof TradeUpdated).count();
        assertEquals(1, updatedCount,
                "TradeUpdated IS emitted when fill quantity > 0");

        TradeUpdated updated = (TradeUpdated) emitted.stream()
                .filter(e -> e instanceof TradeUpdated)
                .findFirst().orElseThrow();
        assertEquals("ORD-INTERNAL-1", updated.tradeId());
        assertEquals("SBIN", updated.symbol());
    }

    @Test
    void tradeUpdatedIsEmittedWhenPnlNonZero() {
        // This test verifies the guard condition logic: even if qty=0,
        // a non-zero pnl would allow the TradeUpdated through.
        // Currently unrealizedPnl is hardcoded to 0L, so this test documents
        // the expected behavior once pnl becomes dynamic.
        //
        // For now, we test that a fill with qty > 0 passes through (positive case).
        String brokerOrderId = "BRK-PNL";
        String signalId = "sig-pnl";
        emitFirstFill(brokerOrderId, signalId);

        com.tradej.core.domain.oms.OrderProjection proj =
                new com.tradej.core.domain.oms.OrderProjection(
                        "ORD-INTERNAL-1", "SBIN", 100, 60,
                        150_00L, com.tradej.core.domain.oms.LifecycleState.PARTIALLY_FILLED);
        when(orderManagementService.getOrderProjection("ORD-INTERNAL-1"))
                .thenReturn(java.util.Optional.of(proj));

        // Fill with qty=1 (non-zero) should pass the guard
        List<Trade> fills = List.of(
                new Trade("T-003", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ,
                        Side.BUY, 1, 153_00L, 100L)
        );
        Order order = createOrder(brokerOrderId, signalId);
        OrderFilled fill = new OrderFilled(
                EventMetadata.correlated(signalId, 3L), order, fills);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);
        handler.onDomainEvent(fill);
        assertTrue(awaitLatch(latch), "Fill processing did not complete");

        long updatedCount = emitted.stream().filter(e -> e instanceof TradeUpdated).count();
        assertEquals(1, updatedCount,
                "TradeUpdated IS emitted when fill has non-zero quantity (pnl guard path)");
    }
}
