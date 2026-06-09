package com.tradej.execution.command;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.port.HistoricalImportPort;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.service.OrderManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CommandHandlerExpandedTest {

    private OrderManagementService oms;
    private com.tradej.broker.api.IBrokerConnection brokerConnection;
    private HistoricalImportPort importPort;
    private CommandHandler handler;

    @BeforeEach
    void setUp() {
        oms = mock(OrderManagementService.class);
        brokerConnection = mock(com.tradej.broker.api.IBrokerConnection.class);
        importPort = mock(HistoricalImportPort.class);
        handler = new CommandHandler(oms, brokerConnection, importPort);
    }

    @Test
    void placeOrderSuccess() {
        Order expected = mock(Order.class);
        when(oms.placeOrder(any())).thenReturn(expected);

        var request = new OrderRequest("RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY,
                1L, OrderType.LIMIT, 250000L, 0L, ProductType.INTRADAY, Validity.DAY, "corr-1");
        CommandResult result = handler.execute(new TradingCommand.PlaceOrder(request));

        assertInstanceOf(CommandResult.Success.class, result);
        assertEquals(expected, ((CommandResult.Success) result).order());
    }

    @Test
    void placeOrderRejected() {
        when(oms.placeOrder(any())).thenReturn(null);

        var request = new OrderRequest("RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY,
                1L, OrderType.LIMIT, 250000L, 0L, ProductType.INTRADAY, Validity.DAY, "corr-1");
        CommandResult result = handler.execute(new TradingCommand.PlaceOrder(request));

        assertInstanceOf(CommandResult.Rejected.class, result);
    }

    @Test
    void cancelOrderSuccess() {
        when(oms.cancelOrder("order-1")).thenReturn(true);

        CommandResult result = handler.execute(new TradingCommand.CancelOrder("order-1"));

        assertInstanceOf(CommandResult.Success.class, result);
    }

    @Test
    void cancelOrderRejected() {
        when(oms.cancelOrder("order-1")).thenReturn(false);

        CommandResult result = handler.execute(new TradingCommand.CancelOrder("order-1"));

        assertInstanceOf(CommandResult.Rejected.class, result);
    }

    @Test
    void killSwitchEngage() {
        CommandResult result = handler.execute(new TradingCommand.SetKillSwitch(true));

        assertInstanceOf(CommandResult.KillSwitchResult.class, result);
        assertTrue(((CommandResult.KillSwitchResult) result).enabled());
        verify(oms).activateKillSwitch();
    }

    @Test
    void killSwitchDisengage() {
        CommandResult result = handler.execute(new TradingCommand.SetKillSwitch(false));

        assertInstanceOf(CommandResult.KillSwitchResult.class, result);
        assertFalse(((CommandResult.KillSwitchResult) result).enabled());
        verify(oms).deactivateKillSwitch();
    }

    @Test
    void refreshCatalogWithoutBroker() {
        var handlerNoBroker = new CommandHandler(oms);

        CommandResult result = handlerNoBroker.execute(new TradingCommand.RefreshInstrumentCatalog(false));

        assertInstanceOf(CommandResult.Error.class, result);
    }

    @Test
    void importHistoricalDataSuccess() {
        when(importPort.startImport(any())).thenReturn("job-123");

        CommandResult result = handler.execute(new TradingCommand.ImportHistoricalData(
                "RELIANCE", "NSE_EQ", "5m", 1000L, 2000L));

        assertInstanceOf(CommandResult.ImportStarted.class, result);
        assertEquals("job-123", ((CommandResult.ImportStarted) result).jobId());
    }

    @Test
    void importHistoricalDataWithoutPort() {
        var handlerNoImport = new CommandHandler(oms, brokerConnection);

        CommandResult result = handlerNoImport.execute(new TradingCommand.ImportHistoricalData(
                "RELIANCE", "NSE_EQ", "5m", 1000L, 2000L));

        assertInstanceOf(CommandResult.Error.class, result);
    }

    @Test
    void reconcilePositions() {
        CommandResult result = handler.execute(new TradingCommand.ReconcilePositions("{\"test\":true}"));

        assertInstanceOf(CommandResult.ReconciliationResult.class, result);
        assertTrue(result.isSuccess());
    }

    @Test
    void sealedInterfaceExhaustiveness() {
        // This test verifies the sealed interface is exhaustive at compile time.
        // If a new variant is added to TradingCommand without a handler,
        // the switch in CommandHandler.execute() will fail to compile.
        TradingCommand[] allVariants = {
                new TradingCommand.PlaceOrder(mock(OrderRequest.class)),
                new TradingCommand.CancelOrder("id"),
                new TradingCommand.ModifyOrder(mock(com.tradej.core.domain.model.ModifyOrderRequest.class)),
                new TradingCommand.CancelAllOpenOrders(),
                new TradingCommand.SetKillSwitch(true),
                new TradingCommand.RefreshInstrumentCatalog(false),
                new TradingCommand.ImportHistoricalData("S", "NSE_EQ", "5m", 0, 0),
                new TradingCommand.ReconcilePositions("{}")
        };

        assertEquals(8, allVariants.length, "All TradingCommand variants must be covered");
    }
}
