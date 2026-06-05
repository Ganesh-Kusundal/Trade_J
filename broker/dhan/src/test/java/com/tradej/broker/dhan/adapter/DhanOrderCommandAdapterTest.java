package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.broker.dhan.validator.DhanOrderValidator;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@Tag("unit")
class DhanOrderCommandAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DhanClientHolder clientHolder;
    private DhanInstrumentResolver instrumentResolver;
    private DhanOrderValidator validator;
    private MarketDataProvider marketDataProvider;
    private DhanRetryExecutor executor;
    private DhanConnectionSettings settings;
    private DhanRestOrderClient restOrderClient;
    private IdempotencyCachePort idempotencyCache;
    private DhanOrderCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        clientHolder = mock(DhanClientHolder.class);
        DhanInstrumentDefinition equityInstrument = equityInstrument();
        InMemoryInstrumentResolver realResolver = new InMemoryInstrumentResolver();
        realResolver.replaceDefinitions(List.of(equityInstrument));
        instrumentResolver = realResolver;
        marketDataProvider = mock(MarketDataProvider.class);
        restOrderClient = mock(DhanRestOrderClient.class);
        idempotencyCache = mock(IdempotencyCachePort.class);
        settings = DhanConnectionSettings.withDefaults(
                "2505162156",
                "test-token",
                DhanApiEnvironment.LIVE,
                null,
                DhanAuthMode.STATIC,
                Path.of("config/dhan-pin.txt"),
                Path.of("config/dhan-totp-secret.txt"),
                Path.of("runtime/dhan-token-state.json"),
                10L
        );
        executor = mock(DhanRetryExecutor.class);

        validator = new DhanOrderValidator(instrumentResolver, settings, marketDataProvider, false);

        when(executor.execute(any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(2);
            return supplier.get();
        });
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(executor).run(any(), any(), any());

        adapter = new DhanOrderCommandAdapter(
                clientHolder, instrumentResolver, executor, settings, restOrderClient, idempotencyCache, validator);
    }

    private DhanInstrumentDefinition equityInstrument() {
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

    private OrderRequest equityLimitOrder() {
        return new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10L,
                OrderType.LIMIT,
                350000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation-" + System.currentTimeMillis()
        );
    }

    @Nested
    @DisplayName("modifyOrder REST path")
    class ModifyOrderTests {

        @Test
        @DisplayName("modifyOrder makes single REST call and returns updated order")
        void modifyOrderSingleCallReturnsUpdatedOrder() {
            ModifyOrderRequest request = new ModifyOrderRequest(
                    "ORDER123",
                    15L,
                    355000L,
                    null,
                    null,
                    null
            );
            when(restOrderClient.modifyOrderViaApi(any(), isNull(), eq(settings)))
                    .thenReturn(orderJson("ORDER123", 3550.0, 15, 5));

            Order result = adapter.modifyOrder(request);

            assertEquals("ORDER123", result.orderId());
            assertEquals(15L, result.quantity());
            assertEquals(355000L, result.pricePaisa());
            verify(restOrderClient, times(1)).modifyOrderViaApi(any(), isNull(), eq(settings));
        }

        @Test
        @DisplayName("modifyOrder throws if REST call fails")
        void modifyOrderThrowsOnRestFailure() {
            ModifyOrderRequest request = new ModifyOrderRequest(
                    "ORDER123",
                    15L,
                    355000L,
                    null,
                    null,
                    null
            );
            when(restOrderClient.modifyOrderViaApi(any(), isNull(), eq(settings)))
                    .thenThrow(new RuntimeException("REST modify failed"));

            assertThrows(RuntimeException.class, () -> adapter.modifyOrder(request));
            verify(restOrderClient, times(1)).modifyOrderViaApi(any(), isNull(), eq(settings));
        }
    }

    @Nested
    @DisplayName("placeOrder catalog guard")
    class PlaceOrderCatalogGuardTests {

        @Test
        @DisplayName("placeOrder throws when catalog not loaded")
        void placeOrderThrowsWhenCatalogNotLoaded() {
            InMemoryInstrumentResolver emptyResolver = new InMemoryInstrumentResolver();
            DhanOrderCommandAdapter guardedAdapter = new DhanOrderCommandAdapter(
                    clientHolder, emptyResolver, executor, settings, restOrderClient, idempotencyCache, validator);
            OrderRequest request = equityLimitOrder();

            assertThrows(IllegalStateException.class, () -> guardedAdapter.placeOrder(request));
        }

        @Test
        @DisplayName("placeOrder proceeds when catalog is loaded")
        void placeOrderProceedsWhenCatalogLoaded() {
            when(restOrderClient.placeOrderViaApi(any(), any(), eq(settings)))
                    .thenReturn(orderJson("ORDER456", 3500.0, 10, 0));

            Order result = adapter.placeOrder(equityLimitOrder());

            assertEquals("ORDER456", result.orderId());
            verify(restOrderClient, times(1)).placeOrderViaApi(any(), any(), eq(settings));
        }
    }

    private ObjectNode orderJson(String orderId, double price, int quantity, int filledQty) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("orderId", orderId);
        node.put("securityId", "11536");
        node.put("exchangeSegment", "NSE_EQ");
        node.put("transactionType", "BUY");
        node.put("productType", "INTRADAY");
        node.put("orderType", "LIMIT");
        node.put("price", price);
        node.put("quantity", quantity);
        node.put("filledQty", filledQty);
        node.put("orderStatus", "OPEN");
        node.put("validity", "DAY");
        return node;
    }
}
