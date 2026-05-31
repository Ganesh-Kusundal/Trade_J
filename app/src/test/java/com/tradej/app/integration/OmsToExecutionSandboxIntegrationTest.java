package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.oms.LifecycleState;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderStateMachine;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("cross-layer")
class OmsToExecutionSandboxIntegrationTest {
    private DhanBrokerConnection brokerConnection;
    private ExecutionHandler executionHandler;
    private EventSourcedOrderRepository omsRepository;

    @AfterEach
    void tearDown() {
        if (executionHandler != null) {
            executionHandler.stop();
        }
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (omsRepository != null) {
            omsRepository.close();
        }
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void orderStateMachineTracksSubmittedToAcknowledged() {
        OrderStateMachine machine = new OrderStateMachine("ORD-1", "TCS", 10L);
        machine.on(OrderSubmitted.create("ORD-1", "sig-1", "TCS", 10L));
        machine.on(OrderAcknowledged.event("ORD-1", "broker-1"));
        assertEquals(LifecycleState.SUBMITTED, machine.currentStatus());
    }

    @Test
    void sandboxBrokerIdempotencyPreventsDuplicatePlacement() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_CROSS_LAYER_TEST_ENABLED", "dhan.crossLayerTestEnabled", "false")),
                "Set DHAN_CROSS_LAYER_TEST_ENABLED=true to run cross-layer sandbox execution tests.");

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("oms-xlayer-catalog"), false);

        String correlationId = "omsdup" + System.currentTimeMillis();
        OrderRequest request = new OrderRequest(
                LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ")),
                Side.BUY,
                1L,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                correlationId
        );

        Order first = brokerConnection.orders().placeOrder(request);
        Order second = brokerConnection.orders().placeOrder(request);
        assertFalse(first.orderId().isBlank());
        assertEquals(first.orderId(), second.orderId(), "Idempotency cache should return the same sandbox order.");
        LiveDhanTestSupport.trackSandboxOrderForCleanup(first.orderId());
    }

    @Test
    void executionHandlerDoesNotDoublePlaceForSameCorrelation() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_CROSS_LAYER_TEST_ENABLED", "dhan.crossLayerTestEnabled", "false")),
                "Set DHAN_CROSS_LAYER_TEST_ENABLED=true to run cross-layer sandbox execution tests.");

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("oms-exec-catalog"), false);

        Path omsPath = Files.createTempDirectory("oms-exec-repo");
        omsRepository = new EventSourcedOrderRepository(omsPath);
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        executionHandler = new ExecutionHandler(
                omsRepository,
                new OrderManagementService(brokerConnection, runtimeModeHolder),
                runtimeModeHolder,
                new TradingCircuitBreaker(),
                new OrderIdentityRegistry(),
                com.tradej.core.domain.port.DeadLetterQueue.noop()
        );
        executionHandler.start();

        String correlationId = "omsexec" + System.currentTimeMillis();
        OrderRequest orderRequest = new OrderRequest(
                LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ")),
                Side.BUY,
                1L,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                correlationId
        );

        List<DomainEvent> emitted = new ArrayList<>();

        SignalPendingExecution pending = new SignalPendingExecution(
                EventMetadata.root(),
                "sig-oms",
                orderRequest,
                Map.of()
        );
        executionHandler.onDomainEvent(pending, emitted::add);
        executionHandler.onDomainEvent(pending, emitted::add);

        awaitOrderAcceptedCount(emitted, 2, 45);
        List<OrderAccepted> accepted = emitted.stream().filter(OrderAccepted.class::isInstance).map(OrderAccepted.class::cast).toList();
        assertEquals(2, accepted.size());
        assertEquals(accepted.get(0).order().orderId(), accepted.get(1).order().orderId());
        LiveDhanTestSupport.trackSandboxOrderForCleanup(accepted.get(0).order().orderId());
    }

    private static void awaitOrderAcceptedCount(List<DomainEvent> emitted, int expected, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            long count = emitted.stream().filter(OrderAccepted.class::isInstance).count();
            if (count >= expected) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for " + expected + " OrderAccepted events; got " + emitted);
    }
}
