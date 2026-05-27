package com.tradej.execution.service;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.KillSwitchEngaged;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ExecutionHandlerUnitTest {

    private Path tempDir;
    private EventSourcedOrderRepository omsRepo;

    @Mock
    private OrderManagementService orderManagementService;

    @Mock
    private TradingCircuitBreaker circuitBreaker;

    private ExecutionHandler handler;

    private final List<com.tradej.core.domain.event.DomainEvent> emitted = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("exec-handler-test-");
        omsRepo = new EventSourcedOrderRepository(tempDir);
        handler = new ExecutionHandler(omsRepo, orderManagementService, circuitBreaker);
        emitted.clear();
    }

    @AfterEach
    void tearDown() {
        handler.stop();
        omsRepo.close();
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    // ── SignalPendingExecution flow ─────────────────────────────────────────

    @Test
    void processesSignalAndPlacesOrder() {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-001", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-1", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);

        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        // Verify OSM has the order
        List<String> orderIds = omsRepo.knownOrderIds();
        assertFalse(orderIds.isEmpty());
        OrderProjection proj = omsRepo.rebuild(orderIds.getFirst());
        assertNotNull(proj);

        // Verify downstream events
        boolean hasAccepted = emitted.stream().anyMatch(e -> e instanceof OrderAccepted);
        assertTrue(hasAccepted, "Should emit OrderAccepted");

        verify(orderManagementService).placeOrder(any());
        verify(circuitBreaker).recordSuccess();
    }

    @Test
    void appendsOrderSubmittedToOsmOnSignal() {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-001", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-1", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        // OSM should have the order
        List<String> orderIds = omsRepo.knownOrderIds();
        assertFalse(orderIds.isEmpty());
        OrderProjection proj = omsRepo.rebuild(orderIds.getFirst());
        assertNotNull(proj);
        assertEquals("SBIN", proj.symbol());
    }

    @Test
    void emitsOrderRejectedWhenBrokerRejects() {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order rejectedOrder = createRejectedOrder("EX-001", "Insufficient margin");
        when(orderManagementService.placeOrder(any())).thenReturn(rejectedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-rej", "TCS", 50);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        boolean hasRejected = emitted.stream().anyMatch(e -> e instanceof OrderRejected);
        assertTrue(hasRejected, "Should emit OrderRejected");
    }

    @Test
    void emitsSignalSuppressedWhenOrderPlacementFails() {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        when(orderManagementService.placeOrder(any())).thenThrow(new RuntimeException("Broker unavailable"));

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-fail", "HDFC", 25);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        boolean hasSuppressed = emitted.stream().anyMatch(e -> e instanceof SignalSuppressed);
        assertTrue(hasSuppressed, "Should emit SignalSuppressed on failure");
        verify(circuitBreaker).recordFailure();
    }

    @Test
    void circuitBreakerBlocksWhenOpen() {
        when(circuitBreaker.allowsRequest()).thenReturn(false);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-cb", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        boolean hasKillSwitch = emitted.stream().anyMatch(e -> e instanceof KillSwitchEngaged);
        assertTrue(hasKillSwitch, "Should emit KillSwitchEngaged when circuit breaker is open");
        verify(orderManagementService, never()).placeOrder(any());
    }

    // ── OrderFilled flow ────────────────────────────────────────────────────

    @Test
    void orderFilledAppendsToOsmAndEmitsTradeOpened() {
        // First, set up an order in OSM
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-001", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-fill", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        emitted.clear(); // Clear the accepted event

        // Now simulate OrderFilled from broker
        String orderId = omsRepo.knownOrderIds().getFirst();
        List<Trade> fills = List.of(
                new Trade("T-001", orderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 151_00L, 1000L)
        );
        Order filledOrder = new Order(orderId, "sig-fill", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                100, 100, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-fill", 1L), filledOrder, fills);

        handler.onDomainEvent(orderFilled, emitted::add);

        // Verify OSM state
        OrderProjection proj = omsRepo.rebuild(orderId);
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.FILLED, proj.status());
        assertEquals(100, proj.filledQuantity());

        // Verify TradeOpened was emitted
        boolean hasTradeOpened = emitted.stream().anyMatch(e -> e instanceof TradeOpened);
        assertTrue(hasTradeOpened, "Should emit TradeOpened");
    }

    @Test
    void orderFilledWithoutOsmStateEmitsTradeOpenedDirectly() {
        // Order not tracked in OSM — should use backward compat path
        String orderId = "ORD-UNTRACKED";
        List<Trade> fills = List.of(
                new Trade("T-001", orderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 151_00L, 1000L)
        );
        Order filledOrder = new Order(orderId, "sig-orphan", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                100, 100, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-orphan", 1L), filledOrder, fills);

        handler.onDomainEvent(orderFilled, emitted::add);

        // Should emit TradeOpened even without OSM state
        boolean hasTradeOpened = emitted.stream().anyMatch(e -> e instanceof TradeOpened);
        assertTrue(hasTradeOpened, "Should emit TradeOpened even without OSM state");
    }

    @Test
    void orderFilledWithEmptyFillsEmitsTradeOpened() {
        // Set up order in OSM first
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-001", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-empty-fill", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal, emitted::add);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");
        emitted.clear();

        // OrderFilled with empty fills — should guard against division by zero
        String orderId = omsRepo.knownOrderIds().getFirst();
        Order filledOrder = new Order(orderId, "sig-empty-fill", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                100, 50, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-empty-fill", 1L), filledOrder, List.of());

        handler.onDomainEvent(orderFilled, emitted::add);

        // Should handle gracefully (empty fills guard)
        boolean hasTradeOpened = emitted.stream().anyMatch(e -> e instanceof TradeOpened);
        assertTrue(hasTradeOpened, "Should emit TradeOpened even with empty fills");
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Test
    void startAndStopWorkCorrectly() {
        handler.start();
        handler.stop();
        // No exception should be thrown
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static SignalPendingExecution createSignal(String signalId, String symbol, long quantity) {
        OrderRequest request = new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, Side.BUY, quantity,
                OrderType.LIMIT, 150_00L, 0L, ProductType.INTRADAY, Validity.DAY, signalId
        );
        return new SignalPendingExecution(
                EventMetadata.correlated(signalId, 1L), signalId, request, Map.of()
        );
    }

    private static Order createOrder(String exchangeOrderId, OrderStatus status) {
        return new Order(
                exchangeOrderId, "sig-1", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, status,
                100, 0, 150_00L, 0L, 1000L, ""
        );
    }

    private static Order createRejectedOrder(String exchangeOrderId, String reason) {
        return new Order(
                exchangeOrderId, "sig-rej", "TCS", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.REJECTED,
                50, 0, 3_200_00L, 0L, 1000L, reason
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
}
