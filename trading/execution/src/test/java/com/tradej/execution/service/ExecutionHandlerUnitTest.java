package com.tradej.execution.service;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.KillSwitchEngaged;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
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
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.execution.identity.OrderIdentityRegistry;
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
    private OrderIdentityRegistry identityRegistry;

    private final List<com.tradej.core.domain.event.DomainEvent> emitted = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("exec-handler-test-");
        omsRepo = new EventSourcedOrderRepository(tempDir);
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

        // Wire mock OrderManagementService to delegate state operations to real omsRepo
        lenient().doAnswer(inv -> {
            com.tradej.core.domain.oms.OrderEvent event = inv.getArgument(0);
            omsRepo.append(event);
            return null;
        }).when(orderManagementService).onBrokerEvent(any());

        lenient().doAnswer(inv -> {
            String orderId = inv.getArgument(0);
            return java.util.Optional.ofNullable(omsRepo.rebuild(orderId));
        }).when(orderManagementService).getOrderProjection(any());
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
        handler.onDomainEvent(signal);

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
        handler.onDomainEvent(signal);
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
        handler.onDomainEvent(signal);
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
        handler.onDomainEvent(signal);
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
        handler.onDomainEvent(signal);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        boolean hasKillSwitch = emitted.stream().anyMatch(e -> e instanceof KillSwitchEngaged);
        assertTrue(hasKillSwitch, "Should emit KillSwitchEngaged when circuit breaker is open");
        verify(orderManagementService, never()).placeOrder(any());
    }

    // ── OrderFilled flow ────────────────────────────────────────────────────

    @Test
    void orderFilledAppendsToOsmAndEmitsDomainEvents() {
        // First, set up an order in OSM
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-001", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-fill", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        emitted.clear(); // Clear the accepted event

        // Now simulate OrderFilled from broker (broker order id, not internal ORD-*)
        String internalOrderId = omsRepo.knownOrderIds().getFirst();
        String brokerOrderId = "EX-001";
        List<Trade> fills = List.of(
                new Trade("T-001", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 151_00L, 1000L)
        );
        Order filledOrder = new Order(brokerOrderId, "sig-fill", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                100, 100, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-fill", 1L), filledOrder, fills);

        CountDownLatch fillLatch = new CountDownLatch(1);
        handler.setProcessingLatch(fillLatch);
        handler.onDomainEvent(orderFilled);
        assertTrue(awaitLatch(fillLatch), "Fill processing did not complete in time");

        // Verify OSM state
        OrderProjection proj = omsRepo.rebuild(internalOrderId);
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.FILLED, proj.status());
        assertEquals(100, proj.filledQuantity());

        // Verify domain event was emitted for fully filled
        boolean hasFullyFilled = emitted.stream().anyMatch(e -> e instanceof OrderFullyFilled);
        assertTrue(hasFullyFilled, "Should emit domain OrderFullyFilled");
        OrderFullyFilled fullyFilled = (OrderFullyFilled) emitted.stream()
                .filter(e -> e instanceof OrderFullyFilled)
                .findFirst().get();
        assertEquals(brokerOrderId, fullyFilled.order().orderId());
        assertEquals(1, fullyFilled.fills().size());

        // Verify TradeOpened was emitted
        boolean hasTradeOpened = emitted.stream().anyMatch(e -> e instanceof TradeOpened);
        assertTrue(hasTradeOpened, "Should emit TradeOpened");
    }

    @Test
    void orderPartiallyFilledEmitsDomainEvent() {
        // Set up an order in OSM with larger quantity
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-100", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-partial", "SBIN", 200);
        handler.start();
        handler.onDomainEvent(signal);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");
        emitted.clear();

        // Simulate partial fill: 50 out of 200
        String internalOrderId = omsRepo.knownOrderIds().getFirst();
        String brokerOrderId = "EX-100";
        List<Trade> fills = List.of(
                new Trade("T-002", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 50, 151_00L, 500L)
        );
        Order filledOrder = new Order(brokerOrderId, "sig-partial", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                200, 50, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-partial", 1L), filledOrder, fills);

        CountDownLatch fillLatch = new CountDownLatch(1);
        handler.setProcessingLatch(fillLatch);
        handler.onDomainEvent(orderFilled);
        assertTrue(awaitLatch(fillLatch), "Fill processing did not complete in time");

        // Verify OSM state is PARTIALLY_FILLED
        OrderProjection proj = omsRepo.rebuild(internalOrderId);
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.PARTIALLY_FILLED, proj.status());
        assertEquals(50, proj.filledQuantity());

        // Verify domain event was emitted for partially filled
        boolean hasPartiallyFilled = emitted.stream().anyMatch(e -> e instanceof OrderPartiallyFilled);
        assertTrue(hasPartiallyFilled, "Should emit domain OrderPartiallyFilled");

        // Verify TradeOpened was emitted
        boolean hasTradeOpened = emitted.stream().anyMatch(e -> e instanceof TradeOpened);
        assertTrue(hasTradeOpened, "Should emit TradeOpened");
    }

    @Test
    void orderFilledWithoutIdentityMappingIsDroppedAfterDefer() throws Exception {
        handler.start();
        String brokerOrderId = "BRK-ORPHAN";
        List<Trade> fills = List.of(
                new Trade("T-001", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 100, 151_00L, 1000L)
        );
        Order filledOrder = new Order(brokerOrderId, "sig-orphan", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                100, 100, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-orphan", 1L), filledOrder, fills);

        handler.onDomainEvent(orderFilled);
        long deadline = System.currentTimeMillis() + 3_000L;
        while (handler.droppedFillCount() < 1L && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }

        assertTrue(emitted.isEmpty(), "Untracked fills must not emit domain events");
        assertTrue(handler.droppedFillCount() >= 1L, "Fill should be dropped after defer attempts exhaust");
    }

    @Test
    void secondFillEmitsTradeUpdatedInsteadOfTradeOpened() {
        // First fill: set up order and emit first OrderFilled
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order placedOrder = createOrder("EX-002", OrderStatus.OPEN);
        when(orderManagementService.placeOrder(any())).thenReturn(placedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-dual", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");
        emitted.clear(); // Clear OrderAccepted

        String internalOrderId = omsRepo.knownOrderIds().getFirst();
        String brokerOrderId = "EX-002";

        // First fill — should produce TradeOpened
        List<Trade> fills1 = List.of(
                new Trade("T-001", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 60, 151_00L, 600L)
        );
        Order filledOrder1 = new Order(brokerOrderId, "sig-dual", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.TRADED,
                100, 60, 150_00L, 0L, 1000L, "");
        OrderFilled firstFill = new OrderFilled(
                EventMetadata.correlated("sig-dual", 1L), filledOrder1, fills1);

        CountDownLatch fillLatch1 = new CountDownLatch(1);
        handler.setProcessingLatch(fillLatch1);
        handler.onDomainEvent(firstFill);
        assertTrue(awaitLatch(fillLatch1), "First fill processing did not complete in time");

        long tradeOpenedCount = emitted.stream().filter(e -> e instanceof TradeOpened).count();
        assertEquals(1, tradeOpenedCount, "First fill should emit exactly one TradeOpened");
        long tradeUpdatedCountBefore = emitted.stream().filter(e -> e instanceof TradeUpdated).count();
        assertEquals(0, tradeUpdatedCountBefore, "First fill should not emit TradeUpdated");

        // Second fill for the same broker order — should produce TradeUpdated, not TradeOpened
        List<Trade> fills2 = List.of(
                new Trade("T-002", brokerOrderId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 40, 152_00L, 400L)
        );
        Order filledOrder2 = new Order(brokerOrderId, "sig-dual", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.TRADED,
                100, 100, 150_00L, 0L, 1000L, "");
        OrderFilled secondFill = new OrderFilled(
                EventMetadata.correlated("sig-dual", 2L), filledOrder2, fills2);

        CountDownLatch fillLatch2 = new CountDownLatch(1);
        handler.setProcessingLatch(fillLatch2);
        handler.onDomainEvent(secondFill);
        assertTrue(awaitLatch(fillLatch2), "Second fill processing did not complete in time");

        long finalTradeOpenedCount = emitted.stream().filter(e -> e instanceof TradeOpened).count();
        assertEquals(1, finalTradeOpenedCount, "Should still have exactly one TradeOpened across both fills");
        long tradeUpdatedCount = emitted.stream().filter(e -> e instanceof TradeUpdated).count();
        assertEquals(1, tradeUpdatedCount, "Second fill should emit exactly one TradeUpdated");

        TradeUpdated updated = (TradeUpdated) emitted.stream()
                .filter(e -> e instanceof TradeUpdated)
                .findFirst().get();
        assertEquals(internalOrderId, updated.tradeId(),
                "TradeUpdated should reference the internal order ID");
    }

    @Test
    void orderPlacementTimeoutEmitsSignalSuppressed() {
        handler.stop();
        handler = new ExecutionHandler(
                orderManagementService,
                new RuntimeModeHolder(),
                new com.tradej.core.domain.time.LiveTradingClock(),
                circuitBreaker,
                identityRegistry,
                DeadLetterQueue.noop(),
                ExecutionConfig.DEFAULTS.withQueueCapacity(1000).withTimeout(200L).withDownstream(emitted::add));
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        when(orderManagementService.placeOrder(any())).thenAnswer(invocation -> {
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return createOrder("EX-003", OrderStatus.OPEN);
        });

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-timeout", "SBIN", 100);
        handler.start();
        handler.onDomainEvent(signal);

        assertTrue(awaitLatch(latch), "Processing did not complete within timeout window");

        boolean hasSuppressed = emitted.stream().anyMatch(e -> e instanceof SignalSuppressed);
        assertTrue(hasSuppressed, "Should emit SignalSuppressed on broker timeout");
        boolean hasSuppressedWithTimeout = emitted.stream()
                .filter(e -> e instanceof SignalSuppressed)
                .anyMatch(e -> ((SignalSuppressed) e).reason().toLowerCase().contains("timeout")
                        || ((SignalSuppressed) e).reason().toLowerCase().contains("timed out"));
        assertTrue(hasSuppressedWithTimeout, "Suppression reason should mention timeout");
        assertEquals(0, identityRegistry.size(), "Timed-out placement must release identity mapping");
        verify(circuitBreaker).recordFailure();
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
        handler.onDomainEvent(signal);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");
        emitted.clear();

        // OrderFilled with empty fills — should guard against division by zero
        String internalOrderId = omsRepo.knownOrderIds().getFirst();
        String brokerOrderId = "EX-001";
        Order filledOrder = new Order(brokerOrderId, "sig-empty-fill", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, com.tradej.core.domain.value.OrderStatus.TRADED,
                100, 50, 150_00L, 0L, 1000L, "");
        OrderFilled orderFilled = new OrderFilled(
                EventMetadata.correlated("sig-empty-fill", 1L), filledOrder, List.of());

        CountDownLatch fillLatch = new CountDownLatch(1);
        handler.setProcessingLatch(fillLatch);
        handler.onDomainEvent(orderFilled);
        assertTrue(awaitLatch(fillLatch), "Fill processing did not complete in time");

        // Should handle gracefully (empty fills guard)
        boolean hasTradeOpened = emitted.stream().anyMatch(e -> e instanceof TradeOpened);
        assertTrue(hasTradeOpened, "Should emit TradeOpened even with empty fills");
    }

    // ── Consistency invariants ────────────────────────────────────────────

    @Test
    void toOsmEventPreservesReason() {
        Order order = createRejectedOrder("EX-001", "Insufficient margin");
        var domainEvent = new OrderRejected(
                EventMetadata.correlated("sig-rej", 1L),
                order,
                order.rejectionReason()
        );
        String omsOrderId = "ORD-TEST-001";
        var osmEvent = domainEvent.toOsmEvent(omsOrderId);

        assertEquals("Insufficient margin", osmEvent.reason(),
                "OSM event reason must match domain event reason");
        assertEquals(omsOrderId, osmEvent.orderId(),
                "OSM event orderId must match the provided OSM order ID");
    }

    @Test
    void orderRejectedReasonIsConsistentBetweenOsmAndDomainEvent() {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        Order rejectedOrder = createRejectedOrder("EX-001", "Insufficient margin");
        when(orderManagementService.placeOrder(any())).thenReturn(rejectedOrder);

        CountDownLatch latch = new CountDownLatch(1);
        handler.setProcessingLatch(latch);

        SignalPendingExecution signal = createSignal("sig-rej-consistency", "TCS", 50);
        handler.start();
        handler.onDomainEvent(signal);
        assertTrue(awaitLatch(latch), "Processing did not complete in time");

        // Capture the domain event
        OrderRejected domainRejected = (OrderRejected) emitted.stream()
                .filter(e -> e instanceof OrderRejected)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No OrderRejected emitted"));

        // Verify OSM state is REJECTED
        List<String> orderIds = omsRepo.knownOrderIds();
        assertFalse(orderIds.isEmpty());
        String omsOrderId = orderIds.getFirst();
        OrderProjection proj = omsRepo.rebuild(omsOrderId);
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.REJECTED, proj.status(),
                "OSM state must be REJECTED");

        // Verify domain event reason matches the expected broker rejection
        assertEquals("Insufficient margin", domainRejected.reason(),
                "Domain event reason must match broker rejection reason");
        assertTrue(omsOrderId.startsWith("ORD-"),
                "OSM order ID must start with ORD-");

        // Verify the Order in the domain event carries the exchange order ID
        assertEquals("EX-001", domainRejected.order().orderId(),
                "Domain event Order must carry the broker exchange order ID");
    }

    // ── Order ID format ───────────────────────────────────────────────────

    @Test
    void generatedOrderIdUsesClockAndSequenceFormat() {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        when(orderManagementService.placeOrder(any())).thenReturn(createOrder("EX-001", OrderStatus.OPEN));

        CountDownLatch firstLatch = new CountDownLatch(1);
        handler.setProcessingLatch(firstLatch);
        handler.start();
        handler.onDomainEvent(createSignal("sig-id-1", "SBIN", 100));
        assertTrue(awaitLatch(firstLatch), "First order did not complete in time");

        CountDownLatch secondLatch = new CountDownLatch(1);
        handler.setProcessingLatch(secondLatch);
        handler.onDomainEvent(createSignal("sig-id-2", "SBIN", 100));
        assertTrue(awaitLatch(secondLatch), "Second order did not complete in time");

        List<String> orderIds = omsRepo.knownOrderIds();
        assertEquals(2, orderIds.size());
        assertTrue(orderIds.stream().allMatch(id -> id.matches("ORD-\\d+-\\d+")),
                "Order IDs must be ORD-<epochMs>-<sequence>, got: " + orderIds);
        assertTrue(!orderIds.get(0).equals(orderIds.get(1)), "Sequential order IDs must differ");
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
