package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeExecutionEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DefaultBacktestFillModelTest {

    private DefaultBacktestFillModel model;
    private OrderRequest buyRequest;

    @BeforeEach
    void setUp() {
        model = new DefaultBacktestFillModel(0.1, 5, 1.0);

        buyRequest = new OrderRequest(
                "SBIN",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                100,
                OrderType.LIMIT,
                80000L,
                0L,
                ProductType.CNC,
                Validity.DAY,
                "corr-1"
        );
    }

    @Test
    void fillsBuyOrderWithSlippage() {
        List<TradeExecutionEvent> fills = model.fillOrder(buyRequest, null);

        assertEquals(1, fills.size());
        TradeExecutionEvent fill = fills.get(0);
        assertEquals("SBIN", fill.symbol());
        assertEquals(Side.BUY, fill.side());
        assertEquals(100, fill.executedQuantity());
        assertTrue(fill.executedPricePaisa() > 80000L);
        assertTrue(fill.exchangeTimestampMs() > 0);
    }

    @Test
    void fillsSellOrderWithNegativeSlippage() {
        OrderRequest sellRequest = new OrderRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ, Side.SELL, 50,
                OrderType.MARKET, 250000L, 0L, ProductType.CNC,
                Validity.DAY, "corr-2"
        );

        List<TradeExecutionEvent> fills = model.fillOrder(sellRequest, null);

        assertEquals(1, fills.size());
        TradeExecutionEvent fill = fills.get(0);
        assertEquals(Side.SELL, fill.side());
        assertTrue(fill.executedPricePaisa() < 250000L);
    }

    @Test
    void returnsEmptyWhenFillRatioZero() {
        DefaultBacktestFillModel zeroFill = new DefaultBacktestFillModel(0, 0, 0);

        List<TradeExecutionEvent> fills = zeroFill.fillOrder(buyRequest, null);

        assertTrue(fills.isEmpty());
    }

    @Test
    void partialFillReturnsPartialQuantity() {
        DefaultBacktestFillModel partial = new DefaultBacktestFillModel(0.1, 5, 0.5);

        List<TradeExecutionEvent> fills = partial.fillOrder(buyRequest, null);

        assertEquals(1, fills.size());
        assertEquals(50, fills.get(0).executedQuantity());
    }

    @Test
    void fillHasCorrelatedMetadata() {
        List<TradeExecutionEvent> fills = model.fillOrder(buyRequest, null);

        TradeExecutionEvent fill = fills.get(0);
        assertEquals("corr-1", fill.metadata().correlationId());
        assertNotNull(fill.metadata().eventId());
        assertNotNull(fill.orderId());
        assertNotNull(fill.tradeId());
    }

    @Test
    void usesTriggerTimestamp() {
        long triggerTs = 1000000L;
        EventMetadata meta = new EventMetadata("evt-1", triggerTs, 0, 1, "corr", 1);
        Candle candle = new Candle("SBIN", "1m", 0, 60000, 80000, 80100, 79900, 80000, 1000, true);
        CandleClosed trigger = new CandleClosed(meta, candle);

        List<TradeExecutionEvent> fills = model.fillOrder(buyRequest, trigger);

        assertEquals(triggerTs + 5, fills.get(0).exchangeTimestampMs());
    }

    @Test
    void zeroPriceHasNoSlippage() {
        OrderRequest zeroPriceRequest = new OrderRequest(
                "TEST", ExchangeSegment.NSE_EQ, Side.BUY, 10,
                OrderType.MARKET, 0L, 0L, ProductType.CNC,
                Validity.DAY, "corr-3"
        );

        List<TradeExecutionEvent> fills = model.fillOrder(zeroPriceRequest, null);

        assertEquals(0L, fills.get(0).executedPricePaisa());
    }

    @Test
    void nullTriggerUsesCurrentTime() {
        List<TradeExecutionEvent> fills = model.fillOrder(buyRequest, null);

        assertTrue(fills.get(0).exchangeTimestampMs() > 0);
    }

    @Test
    void nullRequestThrows() {
        assertThrows(NullPointerException.class, () -> model.fillOrder(null, null));
    }

    @Test
    void defaultConstructorHasReasonableValues() {
        DefaultBacktestFillModel defaultModel = new DefaultBacktestFillModel();

        List<TradeExecutionEvent> fills = defaultModel.fillOrder(buyRequest, null);

        assertEquals(1, fills.size());
        assertEquals(100, fills.get(0).executedQuantity());
    }
}