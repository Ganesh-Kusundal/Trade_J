package com.tradej.cli.command;

import com.tradej.broker.api.port.OrderQuery;
import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.OrderStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class CliTradingCommandsTest {

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliTradingCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }

    @Test
    void orderBookReturnsEmptySafely() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        FakeOrderQuery orderQuery = new FakeOrderQuery();
        try {
            System.setOut(new PrintStream(output));
            var cmd = new CliTradingCommands(TestCliContext.create(), new OutputFormatter(false), orderQuery);
            cmd.orderBook();
            assertTrue(orderQuery.orderBookCalled, "should use injected order query");
            assertEquals("No open orders." + System.lineSeparator(), output.toString());
        } finally {
            System.setOut(originalOut);
        }
    }

    private static final class FakeOrderQuery implements OrderQuery {
        private boolean orderBookCalled;

        @Override
        public Order getOrder(String orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Order> getOrderBook() {
            orderBookCalled = true;
            return List.of();
        }

        @Override
        public List<com.tradej.core.domain.model.Trade> getTradeBook() {
            throw new UnsupportedOperationException();
        }

        @Override
        public OrderStatus getOrderStatus(String orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OptionalLong getExecutedPricePaisa(String orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OptionalLong getExchangeTimeMs(String orderId) {
            throw new UnsupportedOperationException();
        }
    }
}

