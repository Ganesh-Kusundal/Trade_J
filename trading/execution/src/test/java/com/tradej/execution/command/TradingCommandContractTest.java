package com.tradej.execution.command;

import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TradingCommandContractTest {

    @Test
    void commandsAreImmutableRecords() {
        var place = new TradingCommand.PlaceOrder(new OrderRequest(
                "REL", ExchangeSegment.NSE_EQ, Side.BUY, 1L,
                OrderType.LIMIT, 100L, 0L, ProductType.INTRADAY, Validity.DAY, "c1"));
        var cancel = new TradingCommand.CancelOrder("o1");
        var modify = new TradingCommand.ModifyOrder(new ModifyOrderRequest(
                "o1", null, null, 2L, 200L, 0L, null, null));
        var cancelAll = new TradingCommand.CancelAllOpenOrders();
        var killSwitch = new TradingCommand.SetKillSwitch(true);
        var refresh = new TradingCommand.RefreshInstrumentCatalog(true);
        var importCmd = new TradingCommand.ImportHistoricalData("S", "NSE_EQ", "5m", 0L, 1L);
        var reconcile = new TradingCommand.ReconcilePositions("{}");

        assertNotNull(place.request());
        assertEquals("o1", cancel.orderId());
        assertEquals("o1", modify.request().orderId());
        assertNotNull(cancelAll);
        assertTrue(killSwitch.enabled());
        assertTrue(refresh.force());
        assertEquals("S", importCmd.symbol());
        assertEquals("{}", reconcile.jsonPayload());
    }

    @Test
    void commandEqualsAndHashCode() {
        var a = new TradingCommand.CancelOrder("o1");
        var b = new TradingCommand.CancelOrder("o1");
        var c = new TradingCommand.CancelOrder("o2");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void resultsAreSealedAndComplete() {
        CommandResult[] allResults = {
                new CommandResult.Success(null),
                new CommandResult.Rejected("reason"),
                new CommandResult.Error("msg"),
                new CommandResult.BulkSuccess(List.of("o1")),
                new CommandResult.KillSwitchResult(true),
                new CommandResult.CatalogRefreshed(100),
                new CommandResult.ImportStarted("job-1"),
                new CommandResult.ReconciliationResult(0)
        };

        assertEquals(8, allResults.length);
        assertTrue(allResults[0].isSuccess());
        assertFalse(allResults[1].isSuccess());
        assertFalse(allResults[2].isSuccess());
        assertTrue(allResults[3].isSuccess());
        assertTrue(allResults[4].isSuccess());
        assertTrue(allResults[5].isSuccess());
        assertTrue(allResults[6].isSuccess());
        assertTrue(allResults[7].isSuccess());
    }

    @Test
    void errorResultWithCause() {
        var cause = new RuntimeException("network timeout");
        var error = new CommandResult.Error("failed", cause);

        assertEquals("failed", error.message());
        assertSame(cause, error.cause());
    }
}
