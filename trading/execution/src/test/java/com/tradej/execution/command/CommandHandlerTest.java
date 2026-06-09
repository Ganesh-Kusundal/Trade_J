package com.tradej.execution.command;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.execution.service.OrderManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CommandHandlerTest {

    private OrderManagementService oms;
    private IBrokerConnection brokerConnection;
    private CommandHandler handler;

    @BeforeEach
    void setUp() {
        oms = mock(OrderManagementService.class);
        brokerConnection = mock(IBrokerConnection.class);
        OrderCommand orderCommand = mock(OrderCommand.class);
        when(brokerConnection.orders()).thenReturn(orderCommand);
        handler = new CommandHandler(oms, brokerConnection);
    }

    @Test
    void placeOrderSuccess() {
        OrderRequest request = mock(OrderRequest.class);
        Order order = mock(Order.class);
        when(oms.placeOrder(request)).thenReturn(order);

        CommandResult result = handler.execute(new TradingCommand.PlaceOrder(request));

        assertInstanceOf(CommandResult.Success.class, result);
        assertEquals(order, ((CommandResult.Success) result).order());
        verify(oms).placeOrder(request);
    }

    @Test
    void placeOrderRejected() {
        OrderRequest request = mock(OrderRequest.class);
        when(oms.placeOrder(request)).thenReturn(null);

        CommandResult result = handler.execute(new TradingCommand.PlaceOrder(request));

        assertInstanceOf(CommandResult.Rejected.class, result);
    }

    @Test
    void cancelOrderSuccess() {
        when(oms.cancelOrder("ORD-123")).thenReturn(true);

        CommandResult result = handler.execute(new TradingCommand.CancelOrder("ORD-123"));

        assertInstanceOf(CommandResult.Success.class, result);
        verify(oms).cancelOrder("ORD-123");
    }

    @Test
    void cancelOrderRejected() {
        when(oms.cancelOrder("ORD-123")).thenReturn(false);

        CommandResult result = handler.execute(new TradingCommand.CancelOrder("ORD-123"));

        assertInstanceOf(CommandResult.Rejected.class, result);
    }

    @Test
    void modifyOrderSuccess() {
        ModifyOrderRequest request = mock(ModifyOrderRequest.class);
        Order order = mock(Order.class);
        when(oms.modifyOrder(request)).thenReturn(order);

        CommandResult result = handler.execute(new TradingCommand.ModifyOrder(request));

        assertInstanceOf(CommandResult.Success.class, result);
    }

    @Test
    void cancelAllOpenOrders() {
        when(brokerConnection.orders().cancelAllOpenOrders())
                .thenReturn(List.of("ORD-1", "ORD-2"));

        CommandResult result = handler.execute(new TradingCommand.CancelAllOpenOrders());

        assertInstanceOf(CommandResult.BulkSuccess.class, result);
        assertEquals(2, ((CommandResult.BulkSuccess) result).orderIds().size());
    }

    @Test
    void cancelAllWithoutBrokerReturnsError() {
        var handlerWithoutBroker = new CommandHandler(oms);

        CommandResult result = handlerWithoutBroker.execute(new TradingCommand.CancelAllOpenOrders());

        assertInstanceOf(CommandResult.Error.class, result);
    }

    @Test
    void killSwitchEnable() {
        CommandResult result = handler.execute(new TradingCommand.SetKillSwitch(true));

        assertInstanceOf(CommandResult.KillSwitchResult.class, result);
        assertTrue(((CommandResult.KillSwitchResult) result).enabled());
        verify(oms).activateKillSwitch();
    }

    @Test
    void killSwitchDisable() {
        CommandResult result = handler.execute(new TradingCommand.SetKillSwitch(false));

        assertInstanceOf(CommandResult.KillSwitchResult.class, result);
        assertFalse(((CommandResult.KillSwitchResult) result).enabled());
        verify(oms).deactivateKillSwitch();
    }

    @Test
    void exceptionReturnsError() {
        OrderRequest request = mock(OrderRequest.class);
        when(oms.placeOrder(request)).thenThrow(new RuntimeException("Broker down"));

        CommandResult result = handler.execute(new TradingCommand.PlaceOrder(request));

        assertInstanceOf(CommandResult.Error.class, result);
        assertTrue(((CommandResult.Error) result).message().contains("Broker down"));
    }
}
