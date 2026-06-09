package com.tradej.app.e2e;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.bridge.SignalExecutionBridge;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.ExecutionConfig;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("runtime-e2e")
@ExtendWith(MockitoExtension.class)
class OrderExecutionFlowEndToEndTest {

    private EventBus eventBus;
    private RuntimeModeHolder runtimeModeHolder;
    private ExecutionHandler executionHandler;
    private OrderManagementService orderManagementService;
    private OrderIdentityRegistry identityRegistry;
    private TradingCircuitBreaker circuitBreaker;
    private Path tempDir;
    private EventSourcedOrderRepository orderRepository;

    @Mock
    private IBrokerConnection mockBrokerConnection;

    @Mock
    private OrderCommand mockOrderCommand;

    @Mock
    private MarketDataProvider mockMarketData;

    @Mock
    private OrderQuery mockOrderQuery;

    @Mock
    private WebSocketMultiplexer mockWebSocket;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("order-e2e-test-");
        eventBus = new SimpleEventBus();
        runtimeModeHolder = new RuntimeModeHolder();
        identityRegistry = new OrderIdentityRegistry();
        circuitBreaker = new TradingCircuitBreaker();
        orderRepository = new EventSourcedOrderRepository(tempDir);

        orderManagementService = new OrderManagementService(
                mockBrokerConnection, runtimeModeHolder, null,
                new LiveTradingClock(), orderRepository, circuitBreaker
        );

        executionHandler = new ExecutionHandler(
                orderManagementService,
                runtimeModeHolder,
                new LiveTradingClock(),
                circuitBreaker,
                identityRegistry,
                DeadLetterQueue.noop(),
                ExecutionConfig.DEFAULTS
        );

        when(mockBrokerConnection.orders()).thenReturn(mockOrderCommand);
        when(mockBrokerConnection.marketData()).thenReturn(mockMarketData);
        when(mockBrokerConnection.orderQuery()).thenReturn(mockOrderQuery);
        when(mockBrokerConnection.websocket()).thenReturn(mockWebSocket);

        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Test
    void signalGeneratesPendingExecutionAndPlacesOrder() {
        SignalGenerated signal = createSignal("RELIANCE", Side.BUY, 245000L, 100L);

        Optional<SignalPendingExecution> pending = SignalExecutionBridge.toPending(signal);

        assertThat(pending).isPresent();
        assertThat(pending.get().signalId()).isEqualTo(signal.signalId());
        assertThat(pending.get().orderRequest().symbol()).isEqualTo("RELIANCE");
        assertThat(pending.get().orderRequest().side()).isEqualTo(Side.BUY);
        assertThat(pending.get().orderRequest().quantity()).isEqualTo(100L);
        assertThat(pending.get().orderRequest().pricePaisa()).isEqualTo(245000L);
        assertThat(pending.get().orderRequest().orderType()).isEqualTo(OrderType.LIMIT);
        assertThat(pending.get().orderRequest().productType()).isEqualTo(ProductType.INTRADAY);
        assertThat(pending.get().orderRequest().validity()).isEqualTo(Validity.DAY);
    }

    @Test
    void signalWithZeroQuantityProducesEmptyPending() {
        Map<String, Object> attrs = new HashMap<>(Map.of("quantity", 0L));
        SignalGenerated signal = new SignalGenerated(
                EventMetadata.root(), "sig-zero", "RELIANCE", "5m",
                Side.BUY, 245000L, 240000L, 250000L, "test-setup", attrs
        );

        Optional<SignalPendingExecution> pending = SignalExecutionBridge.toPending(signal);

        assertThat(pending).isEmpty();
    }

    @Test
    void signalToOrderRequestPreservesCorrelationId() {
        SignalGenerated signal = createSignal("INFY", Side.SELL, 150000L, 200L);

        Optional<SignalPendingExecution> pending = SignalExecutionBridge.toPending(signal);

        assertThat(pending).isPresent();
        assertThat(pending.get().orderRequest().correlationId()).isEqualTo(signal.signalId());
        assertThat(pending.get().metadata().correlationId()).isEqualTo(signal.signalId());
    }

    @Test
    void orderAcceptedEventIsPublishedWhenBrokerAccepts() {
        Order acceptedOrder = createOrder("ORD-001", "RELIANCE", Side.BUY, OrderStatus.OPEN);
        when(mockOrderCommand.placeOrder(any(OrderRequest.class))).thenReturn(acceptedOrder);

        List<DomainEvent> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(OrderAccepted.class, events::add);

        SignalGenerated signal = createSignal("RELIANCE", Side.BUY, 245000L, 100L);
        Optional<SignalPendingExecution> pending = SignalExecutionBridge.toPending(signal);
        assertThat(pending).isPresent();

        executionHandler.visit(pending.get());

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(OrderAccepted.class);
        OrderAccepted accepted = (OrderAccepted) events.get(0);
        assertThat(accepted.order().orderId()).isEqualTo("ORD-001");
    }

    @Test
    void circuitBreakerRejectsOrderWhenOpen() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        assertThat(circuitBreaker.isOpen()).isTrue();

        OrderRequest request = new OrderRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 100L,
                OrderType.LIMIT, 245000L, 0L, ProductType.INTRADAY,
                Validity.DAY, "test-corr"
        );

        assertThatThrownBy(() -> orderManagementService.placeOrder(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circuit breaker");
    }

    @Test
    void identityRegistryTracksOrderMappings() {
        identityRegistry.register("ORD-001", "BRK-100", "SIG-001");
        identityRegistry.acknowledge("ORD-001", "BRK-100");

        assertThat(identityRegistry.resolveInternalId("BRK-100")).isEqualTo("ORD-001");
        assertThat(identityRegistry.resolveBrokerOrderId("ORD-001")).isEqualTo("BRK-100");
        assertThat(identityRegistry.resolveBySignalId("SIG-001")).isEqualTo("ORD-001");
    }

    @Test
    void orderManagementServiceRejectsCancelForTerminalOrder() {
        Order rejectedOrder = createOrder("ORD-002", "RELIANCE", Side.BUY, OrderStatus.REJECTED);
        when(mockOrderQuery.getOrder("ORD-002")).thenReturn(rejectedOrder);

        boolean cancelled = orderManagementService.cancelOrder("ORD-002");
        assertThat(cancelled).isFalse();
    }

    @Test
    void fullSignalToOrderLifecycle() {
        Order placedOrder = createOrder("ORD-LIFECYCLE", "INFY", Side.SELL, OrderStatus.OPEN);
        when(mockOrderCommand.placeOrder(any(OrderRequest.class))).thenReturn(placedOrder);

        List<DomainEvent> allEmitted = new CopyOnWriteArrayList<>();
        eventBus.subscribe(OrderAccepted.class, allEmitted::add);
        eventBus.subscribe(SignalPendingExecution.class, allEmitted::add);

        SignalGenerated signal = createSignal("INFY", Side.SELL, 150000L, 200L);
        Optional<SignalPendingExecution> pending = SignalExecutionBridge.toPending(signal);
        assertThat(pending).isPresent();

        executionHandler.visit(pending.get());

        assertThat(allEmitted).hasSizeGreaterThanOrEqualTo(1);
        assertThat(allEmitted).anyMatch(e -> e instanceof OrderAccepted);
    }

    private SignalGenerated createSignal(String symbol, Side side, long entryPricePaisa, long quantity) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("quantity", quantity);
        attrs.put("exchangeSegment", ExchangeSegment.NSE_EQ.name());
        return new SignalGenerated(
                EventMetadata.root(), UUID.randomUUID().toString(), symbol, "5m",
                side, entryPricePaisa, entryPricePaisa - 5000L, entryPricePaisa + 5000L,
                "e2e-test", attrs
        );
    }

    private Order createOrder(String orderId, String symbol, Side side, OrderStatus status) {
        return new Order(
                orderId, "corr-" + orderId, symbol, ExchangeSegment.NSE_EQ,
                side, ProductType.INTRADAY, OrderType.LIMIT, status,
                100L, 0L, 245000L, 0L, System.currentTimeMillis(), null
        );
    }
}
