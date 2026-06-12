package com.tradej.app.api;

import com.tradej.app.api.dto.OrderProjectionResponse;
import com.tradej.app.service.OrderApplicationService;
import com.tradej.composition.ExecutionComposition;
import com.tradej.composition.FullComposition;
import com.tradej.core.domain.oms.LifecycleState;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.execution.command.CommandHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("component")
class OrderControllerComponentTest {

    private OrderController controller;
    private OrderManagementService orderManagementService;

    @BeforeEach
    void setUp() throws Exception {
        Path omsDir = Files.createTempDirectory("oms-order-api");
        EventSourcedOrderRepository repository = new EventSourcedOrderRepository(omsDir);
        RuntimeModeHolder runtimeModeHolder = new RuntimeModeHolder();
        runtimeModeHolder.setMode(RuntimeMode.REPLAY);
        orderManagementService = new OrderManagementService(
                null,
                runtimeModeHolder,
                new LiveTradingClock(),
                repository
        );
        PositionRiskHandler riskHandler = new PositionRiskHandler(
                com.tradej.core.domain.model.RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                com.tradej.core.domain.port.NetPositionProvider.empty()
        );
        // Mock FullComposition: OrderApplicationService only consults
        // fullComposition.executionComposition().positionRiskHandler().
        FullComposition fullComposition = mock(FullComposition.class);
        ExecutionComposition executionComposition = mock(ExecutionComposition.class);
        when(fullComposition.executionComposition()).thenReturn(executionComposition);
        when(executionComposition.positionRiskHandler()).thenReturn(riskHandler);
        CommandHandler commandHandler = new CommandHandler(orderManagementService);
        OrderApplicationService orderApplicationService = new OrderApplicationService(
                commandHandler, runtimeModeHolder, fullComposition);
        controller = new OrderController(orderManagementService, orderApplicationService);
        orderManagementService.onBrokerEvent(OrderSubmitted.create("ORD-1", "SIG-1", "SBIN", 10));
        orderManagementService.replayAll();
    }

    @Test
    void listActiveOrdersReturnsNonTerminalProjections() {
        @SuppressWarnings("unchecked")
        List<OrderProjectionResponse> body = (List<OrderProjectionResponse>) controller
                .list("active")
                .getBody();
        assertEquals(1, body.size());
        assertEquals("ORD-1", body.getFirst().orderId());
        assertEquals(LifecycleState.PENDING_SUBMIT.name(), body.getFirst().status());
    }

    @Test
    void listAllIncludesActiveOrders() {
        orderManagementService.onBrokerEvent(OrderSubmitted.create("ORD-2", "SIG-2", "RELIANCE", 5));
        orderManagementService.onBrokerEvent(
                com.tradej.core.domain.oms.OrderAcknowledged.event("ORD-2", "EXCH-ORD-2"));
        orderManagementService.onBrokerEvent(
                com.tradej.core.domain.oms.OrderFullyFilled.event("ORD-2", 5, 250_000L));

        @SuppressWarnings("unchecked")
        List<OrderProjectionResponse> all = (List<OrderProjectionResponse>) controller.list("all").getBody();
        assertTrue(all.size() >= 2);
        assertFalse(all.stream().noneMatch(o -> o.orderId().equals("ORD-1")));
    }
}
