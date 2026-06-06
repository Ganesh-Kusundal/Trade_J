package com.tradej.execution.service;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderCancelled;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link OrderManagementService#cancelOrder(String)} checks local
 * order state before calling the broker, preventing double-cancel and
 * cancel-after-fill bugs.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CancelOrderStateGuardTest {

    @Mock IBrokerConnection brokerConnection;
    @Mock OrderCommand orderCommand;
    @Mock EventSourcedOrderRepository orderRepository;
    @Mock RuntimeModeHolder runtimeModeHolder;
    @Mock TradingClock clock;

    private OrderManagementService oms;

    @BeforeEach
    void setUp() {
        lenient().when(brokerConnection.orders()).thenReturn(orderCommand);
        lenient().when(orderRepository.rebuildStateMachine(anyString())).thenReturn(null);
        oms = new OrderManagementService(brokerConnection, runtimeModeHolder, null, clock, orderRepository);
    }

    /**
     * Drives an order through: SUBMITTED → ACKNOWLEDGED → FILLED.
     */
    private void driveToFilled(String orderId) {
        oms.onBrokerEvent(new OrderSubmitted(orderId, "corr", "RELIANCE", 100));
        oms.onBrokerEvent(new OrderAcknowledged(orderId, "EXCH-" + orderId));
        oms.onBrokerEvent(new OrderFullyFilled(orderId, 100, 25000));
    }

    /**
     * Drives an order through: SUBMITTED → ACKNOWLEDGED → CANCELLED.
     */
    private void driveToCancelled(String orderId) {
        oms.onBrokerEvent(new OrderSubmitted(orderId, "corr", "RELIANCE", 100));
        oms.onBrokerEvent(new OrderAcknowledged(orderId, "EXCH-" + orderId));
        oms.onBrokerEvent(new OrderCancelled(orderId));
    }

    /**
     * Drives an order to SUBMITTED (active, non-terminal state).
     */
    private void driveToSubmitted(String orderId) {
        oms.onBrokerEvent(new OrderSubmitted(orderId, "corr", "RELIANCE", 100));
        oms.onBrokerEvent(new OrderAcknowledged(orderId, "EXCH-" + orderId));
    }

    @Test
    void cancelOfFilledOrderReturnsFalse_noBrokerCall() {
        driveToFilled("ORD-FILLED");

        boolean result = oms.cancelOrder("ORD-FILLED");

        assertFalse(result, "Cancel of FILLED order should return false");
        verify(orderCommand, never()).cancelOrder("ORD-FILLED");
    }

    @Test
    void cancelOfCancelledOrderReturnsFalse_noBrokerCall() {
        driveToCancelled("ORD-CANCELLED");

        boolean result = oms.cancelOrder("ORD-CANCELLED");

        assertFalse(result, "Cancel of CANCELLED order should return false");
        verify(orderCommand, never()).cancelOrder("ORD-CANCELLED");
    }

    @Test
    void cancelOfOpenOrderCallsBroker() {
        driveToSubmitted("ORD-OPEN");
        when(orderCommand.cancelOrder("ORD-OPEN")).thenReturn(true);

        boolean result = oms.cancelOrder("ORD-OPEN");

        assertTrue(result, "Cancel of active order should return true when broker accepts");
        verify(orderCommand, times(1)).cancelOrder("ORD-OPEN");
    }

    @Test
    void cancelOfUnknownOrderCallsBroker() {
        // No state machine, repository returns null → falls through to broker
        when(orderCommand.cancelOrder("ORD-UNKNOWN")).thenReturn(true);

        boolean result = oms.cancelOrder("ORD-UNKNOWN");

        assertTrue(result, "Cancel of unknown order should delegate to broker");
        verify(orderCommand, times(1)).cancelOrder("ORD-UNKNOWN");
    }
}
