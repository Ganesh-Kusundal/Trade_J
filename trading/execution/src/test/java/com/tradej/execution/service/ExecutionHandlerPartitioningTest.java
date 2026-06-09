package com.tradej.execution.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.*;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ExecutionHandlerPartitioningTest {

    @Mock
    private OrderManagementService orderManagementService;

    @Mock
    private TradingCircuitBreaker circuitBreaker;

    private ExecutionHandler handler;
    private final List<DomainEvent> emitted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handler = new ExecutionHandler(
                orderManagementService,
                new RuntimeModeHolder(),
                new com.tradej.core.domain.time.LiveTradingClock(),
                circuitBreaker,
                new OrderIdentityRegistry(),
                DeadLetterQueue.noop(),
                new ExecutionConfig(100, 5_000L, 4, emitted::add)
        );
    }

    @AfterEach
    void tearDown() {
        if (handler != null) {
            handler.stop();
        }
    }

    @Test
    void multiplePartitionsCreatedWithZeroDepth() {
        assertEquals(0, handler.queueDepth());
        assertTrue(handler.queueRemainingCapacity() > 0);
    }

    @Test
    void differentSymbolsProcessedAcrossPartitions() throws InterruptedException {
        when(circuitBreaker.allowsRequest()).thenReturn(true);
        when(orderManagementService.placeOrder(any())).thenReturn(
                new com.tradej.core.domain.model.Order(
                        "EX-001", "sig-1", "NIFTY", ExchangeSegment.NSE_EQ,
                        Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.OPEN,
                        100, 0, 150_00L, 0L, 1000L, ""
                ));

        CountDownLatch latch = new CountDownLatch(2);
        handler.setProcessingLatch(latch);
        handler.start();

        handler.onDomainEvent(createSignal("sig-1", "NIFTY", 100));
        handler.onDomainEvent(createSignal("sig-2", "BANKNIFTY", 50));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Both signals should be processed");
    }

    @Test
    void queueDepthSumsAllPartitions() {
        handler.start();
        assertEquals(0, handler.queueDepth());
    }

    @Test
    void singlePartitionModeBackwardCompatible() {
        ExecutionHandler single = new ExecutionHandler(
                orderManagementService,
                new RuntimeModeHolder(),
                new com.tradej.core.domain.time.LiveTradingClock(),
                circuitBreaker,
                new OrderIdentityRegistry(),
                DeadLetterQueue.noop(),
                new ExecutionConfig(100, 5_000L, 1, emitted::add)
        );
        assertEquals(0, single.queueDepth());
        assertTrue(single.queueRemainingCapacity() > 0);
        single.stop();
    }

    private static SignalPendingExecution createSignal(String signalId, String symbol, long quantity) {
        OrderRequest request = new OrderRequest(
                symbol, ExchangeSegment.NSE_EQ, Side.BUY, quantity,
                OrderType.LIMIT, 150_00L, 0L, ProductType.INTRADAY, Validity.DAY, signalId
        );
        return new SignalPendingExecution(
                EventMetadata.correlated(signalId, 1L), signalId, request, Map.of()
        );
    }
}
