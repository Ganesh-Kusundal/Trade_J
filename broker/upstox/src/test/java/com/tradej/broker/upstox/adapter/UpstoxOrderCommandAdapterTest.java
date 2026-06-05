package com.tradej.broker.upstox.adapter;

import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UpstoxOrderCommandAdapterTest {

    @Mock
    UpstoxOrderRestClient restClient;

    @Mock
    UpstoxDomainMapper mapper;

    @Mock
    UpstoxInstrumentResolver instrumentResolver;

    private UpstoxOrderCommandAdapter createAdapter() {
        return new UpstoxOrderCommandAdapter(restClient, mapper, instrumentResolver);
    }

    @Test
    void previewOrderMarketOrderReturnsValidPreview() {
        when(instrumentResolver.requireInstrumentKey(any(InstrumentKey.class)))
                .thenReturn("NSE_EQ|INE002A01018");

        UpstoxOrderCommandAdapter adapter = createAdapter();

        OrderRequest request = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10,
                OrderType.MARKET,
                0L, // pricePaisa
                0L, // triggerPricePaisa
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation-id"
        );

        OrderPreview preview = adapter.previewOrder(request);

        assertNotNull(preview);
        assertTrue(preview.valid());
        assertEquals("RELIANCE", preview.symbol());
        assertEquals(ExchangeSegment.NSE_EQ, preview.exchangeSegment());
        assertEquals(Side.BUY, preview.side());
        assertEquals(10, preview.quantity());
        assertEquals(0L, preview.pricePaisa());
        assertEquals(0L, preview.triggerPricePaisa());
        assertEquals(ProductType.INTRADAY, preview.productType());
        // For MARKET orders, estimatedNotional is placeholder (quantity * 100000)
        assertEquals(10 * 100000L, preview.estimatedNotionalPaisa());
        assertEquals(0L, preview.estimatedMarginPaisa());
        assertTrue(preview.issues().isEmpty());
    }

    @Test
    void previewOrderLimitOrderReturnsValidPreview() {
        when(instrumentResolver.requireInstrumentKey(any(InstrumentKey.class)))
                .thenReturn("NSE_EQ|INE002A01018");

        UpstoxOrderCommandAdapter adapter = createAdapter();

        OrderRequest request = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.SELL,
                5,
                OrderType.LIMIT,
                250000L, // pricePaisa = 2500.00
                0L,
                ProductType.CNC,
                Validity.DAY,
                "test-correlation-id"
        );

        OrderPreview preview = adapter.previewOrder(request);

        assertNotNull(preview);
        assertTrue(preview.valid());
        assertEquals("RELIANCE", preview.symbol());
        assertEquals(Side.SELL, preview.side());
        assertEquals(5, preview.quantity());
        assertEquals(250000L, preview.pricePaisa());
        // For LIMIT orders, estimatedNotional = quantity * pricePaisa
        assertEquals(5 * 250000L, preview.estimatedNotionalPaisa());
        assertEquals(0L, preview.estimatedMarginPaisa());
        assertTrue(preview.issues().isEmpty());
    }

    @Test
    void previewOrderStopLossOrderReturnsValidPreview() {
        when(instrumentResolver.requireInstrumentKey(any(InstrumentKey.class)))
                .thenReturn("NSE_EQ|INE002A01018");

        UpstoxOrderCommandAdapter adapter = createAdapter();

        OrderRequest request = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                20,
                OrderType.STOP_LOSS,
                245000L, // pricePaisa = 2450.00
                244000L, // triggerPricePaisa = 2440.00
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation-id"
        );

        OrderPreview preview = adapter.previewOrder(request);

        assertNotNull(preview);
        assertTrue(preview.valid());
        assertEquals(OrderType.STOP_LOSS, request.orderType());
        assertEquals(245000L, preview.pricePaisa());
        assertEquals(244000L, preview.triggerPricePaisa());
        assertEquals(20 * 245000L, preview.estimatedNotionalPaisa());
    }

    @Test
    void previewOrderStopLossMarketOrderReturnsValidPreview() {
        when(instrumentResolver.requireInstrumentKey(any(InstrumentKey.class)))
                .thenReturn("NSE_EQ|INE002A01018");

        UpstoxOrderCommandAdapter adapter = createAdapter();

        OrderRequest request = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.SELL,
                15,
                OrderType.STOP_LOSS_MARKET,
                0L, // pricePaisa not used for SL-M
                244000L, // triggerPricePaisa
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation-id"
        );

        OrderPreview preview = adapter.previewOrder(request);

        assertNotNull(preview);
        assertTrue(preview.valid());
        assertEquals(OrderType.STOP_LOSS_MARKET, request.orderType());
        // For SL-M, pricePaisa is 0, so estimatedNotional will be 0
        // This is a limitation of the current implementation
        assertEquals(0L, preview.pricePaisa());
        assertEquals(244000L, preview.triggerPricePaisa());
    }

    @Test
    void previewOrderCallsInstrumentResolver() {
        when(instrumentResolver.requireInstrumentKey(any(InstrumentKey.class)))
                .thenReturn("NSE_EQ|INE002A01018");

        UpstoxOrderCommandAdapter adapter = createAdapter();

        OrderRequest request = new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10,
                OrderType.LIMIT,
                350000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-correlation-id"
        );

        adapter.previewOrder(request);

        verify(instrumentResolver).requireInstrumentKey(
                argThat(key -> key.symbol().equals("TCS") && key.exchangeSegment() == ExchangeSegment.NSE_EQ)
        );
    }
}