package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
class DhanOrderQueryAdapterLiveTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DhanClientHolder clientHolder;
    private DhanInstrumentResolver instrumentResolver;
    private DhanConnectionSettings settings;
    private DhanRestOrderClient restOrderClient;
    private DhanRetryExecutor executor;
    private DhanOrderQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        clientHolder = mock(DhanClientHolder.class);
        restOrderClient = mock(DhanRestOrderClient.class);
        executor = mock(DhanRetryExecutor.class);

        when(executor.execute(any(ApiCategory.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                    return supplier.get();
                });

        InMemoryInstrumentResolver realResolver = new InMemoryInstrumentResolver();
        realResolver.replaceDefinitions(List.of(tcsDefinition()));
        instrumentResolver = realResolver;

        settings = DhanConnectionSettings.liveWithDefaults("2505162156", "test-token");
    }

    private DhanInstrumentDefinition tcsDefinition() {
        return new DhanInstrumentDefinition(
                "TCS",
                "TCS-EQ",
                Exchange.NSE,
                ExchangeSegment.NSE_EQ,
                "11536",
                "EQUITY",
                "",
                null,
                null,
                null,
                1,
                1,
                null
        );
    }

    private ObjectNode tradeNode(String tradeId, String orderId, double price, long quantity, String side) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("exchangeTradeId", tradeId);
        node.put("orderId", orderId);
        node.put("securityId", "11536");
        node.put("tradingSymbol", "TCS");
        node.put("exchangeSegment", "NSE_EQ");
        node.put("transactionType", side);
        node.put("tradedQuantity", quantity);
        node.put("tradedPrice", price);
        node.put("exchangeTime", 1700000000000L);
        return node;
    }

    private void createAdapter() {
        adapter = new DhanOrderQueryAdapter(
                clientHolder, instrumentResolver, executor, settings, restOrderClient
        );
    }

    @Nested
    @DisplayName("getTradeBook live mode")
    class GetTradeBookLiveTests {

        @Test
        @DisplayName("fetches trades via API in live mode")
        void fetchesTradesViaApi() {
            createAdapter();

            ArrayNode data = MAPPER.createArrayNode();
            data.add(tradeNode("TRADE001", "ORDER001", 3500.25, 10, "BUY"));
            data.add(tradeNode("TRADE002", "ORDER001", 3501.00, 5, "BUY"));
            ObjectNode root = MAPPER.createObjectNode();
            root.set("data", data);
            when(restOrderClient.fetchTradesViaApi(eq(settings))).thenReturn(
                    List.of(root.path("data").get(0), root.path("data").get(1))
            );

            List<Trade> trades = adapter.getTradeBook();

            assertEquals(2, trades.size());
            assertEquals("TRADE001", trades.get(0).tradeId());
            assertEquals("ORDER001", trades.get(0).orderId());
            assertEquals(Side.BUY, trades.get(0).side());
            assertEquals(10, trades.get(0).quantity());
            assertEquals(350_025L, trades.get(0).pricePaisa());
            verify(restOrderClient, times(1)).fetchTradesViaApi(eq(settings));
        }

        @Test
        @DisplayName("returns empty list when API returns no data")
        void returnsEmptyWhenNoTrades() {
            createAdapter();

            when(restOrderClient.fetchTradesViaApi(eq(settings))).thenReturn(List.of());

            List<Trade> trades = adapter.getTradeBook();

            assertTrue(trades.isEmpty());
            verify(restOrderClient, times(1)).fetchTradesViaApi(eq(settings));
        }
    }

    @Nested
    @DisplayName("getTradesForOrder live mode")
    class GetTradesForOrderLiveTests {

        @Test
        @DisplayName("fetches trades for specific order via API")
        void fetchesTradesForOrder() {
            createAdapter();

            ArrayNode data = MAPPER.createArrayNode();
            data.add(tradeNode("TRADE001", "ORDER001", 3500.25, 10, "BUY"));
            ObjectNode root = MAPPER.createObjectNode();
            root.set("data", data);
            when(restOrderClient.fetchTradesForOrderViaApi(eq("ORDER001"), eq(settings))).thenReturn(
                    List.of(root.path("data").get(0))
            );

            List<Trade> trades = adapter.getTradesForOrder("ORDER001");

            assertEquals(1, trades.size());
            assertEquals("TRADE001", trades.getFirst().tradeId());
            assertEquals("ORDER001", trades.getFirst().orderId());
            assertEquals(350_025L, trades.getFirst().pricePaisa());
            verify(restOrderClient, times(1)).fetchTradesForOrderViaApi(eq("ORDER001"), eq(settings));
        }

        @Test
        @DisplayName("returns empty list for unknown order")
        void returnsEmptyForUnknownOrder() {
            createAdapter();

            when(restOrderClient.fetchTradesForOrderViaApi(eq("UNKNOWN"), eq(settings))).thenReturn(List.of());

            List<Trade> trades = adapter.getTradesForOrder("UNKNOWN");

            assertTrue(trades.isEmpty());
            verify(restOrderClient, times(1)).fetchTradesForOrderViaApi(eq("UNKNOWN"), eq(settings));
        }
    }

    @Nested
    @DisplayName("getTradeBook sandbox mode")
    class GetTradeBookSandboxTests {

        @Test
        @DisplayName("uses sandbox path when in sandbox mode")
        void usesSandboxPath() {
            settings = DhanConnectionSettings.sandboxWithDefaults("2505162156", "test-token");
            createAdapter();

            when(restOrderClient.getTrades()).thenReturn(List.of(
                    new Trade("TRADE001", "ORDER001", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10, 350025L, 1700000000000L)
            ));

            List<Trade> trades = adapter.getTradeBook();

            assertEquals(1, trades.size());
            assertEquals("TRADE001", trades.getFirst().tradeId());
            verify(restOrderClient, times(1)).getTrades();
            verify(restOrderClient, never()).fetchTradesViaApi(any());
        }
    }

    @Nested
    @DisplayName("getTradesForOrder sandbox mode")
    class GetTradesForOrderSandboxTests {

        @Test
        @DisplayName("filters from full trade book in sandbox mode")
        void filtersFromFullTradeBook() {
            settings = DhanConnectionSettings.sandboxWithDefaults("2505162156", "test-token");
            createAdapter();

            when(restOrderClient.getTrades()).thenReturn(List.of(
                    new Trade("TRADE001", "ORDER001", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10, 350025L, 1700000000000L),
                    new Trade("TRADE002", "ORDER002", "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 5, 351000L, 1700000001000L)
            ));

            List<Trade> trades = adapter.getTradesForOrder("ORDER001");

            assertEquals(1, trades.size());
            assertEquals("TRADE001", trades.getFirst().tradeId());
            verify(restOrderClient, times(1)).getTrades();
        }

        @Test
        @DisplayName("returns empty for order with no trades")
        void returnsEmptyForNoTrades() {
            settings = DhanConnectionSettings.sandboxWithDefaults("2505162156", "test-token");
            createAdapter();

            when(restOrderClient.getTrades()).thenReturn(List.of());

            List<Trade> trades = adapter.getTradesForOrder("NONEXISTENT");

            assertTrue(trades.isEmpty());
        }
    }
}
