package com.tradej.hotpath;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.slf4j.MDC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderPipelineTest {

    private List<DomainEvent> emitted;
    private OrderPipeline pipeline;

    private static final Order SAMPLE_ORDER = new Order(
            "ORD-1", "sig-1", "SBIN", ExchangeSegment.NSE_EQ,
            Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.OPEN,
            100, 0, 750_00L, 0L, 1_000_000L, ""
    );

    @BeforeEach
    void setUp() {
        emitted = new ArrayList<>();
        pipeline = new OrderPipeline(emitted::add);
    }

    @Test
    void orderAcceptedIsForwarded() {
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);

        pipeline.onOrderAccepted(accepted);

        assertEquals(1, emitted.size());
        assertInstanceOf(OrderAccepted.class, emitted.get(0));
        assertEquals(1, pipeline.totalOrdersAccepted());
    }

    @Test
    void nullOrderAcceptedIsIgnored() {
        pipeline.onOrderAccepted(null);
        assertTrue(emitted.isEmpty());
        assertEquals(0, pipeline.totalOrdersAccepted());
    }

    @Test
    void orderFilledIsForwarded() {
        List<Trade> fills = List.of(
                new Trade("fill-1", "ORD-1", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 50, 750_00L, 1_000_000L)
        );
        var filled = new OrderFilled(EventMetadata.root(), SAMPLE_ORDER, fills);

        pipeline.onOrderFilled(filled);

        assertEquals(1, emitted.size());
        assertInstanceOf(OrderFilled.class, emitted.get(0));
    }

    @Test
    void nullOrderFilledIsIgnored() {
        pipeline.onOrderFilled(null);
        assertTrue(emitted.isEmpty());
    }

    @Test
    void orderRejectedIsForwarded() {
        var rejected = new OrderRejected(EventMetadata.root(), SAMPLE_ORDER, "Insufficient margin");

        pipeline.onOrderRejected(rejected);

        assertEquals(1, emitted.size());
        assertInstanceOf(OrderRejected.class, emitted.get(0));
    }

    @Test
    void nullOrderRejectedIsIgnored() {
        pipeline.onOrderRejected(null);
        assertTrue(emitted.isEmpty());
    }

    @Test
    void tradeOpenedIsForwarded() {
        var tradeOpened = new TradeOpened(
                EventMetadata.root(), "TRADE-1", "ORD-1", "sig-1", "SBIN",
                Side.BUY, 100, 750_00L, 740_00L, 770_00L
        );

        pipeline.onTradeOpened(tradeOpened);

        assertEquals(1, emitted.size());
        assertInstanceOf(TradeOpened.class, emitted.get(0));
    }

    @Test
    void tradeUpdatedIsForwarded() {
        var tradeUpdated = new TradeUpdated(
                EventMetadata.root(), "TRADE-1", "SBIN", 752_00L, 2_00L, 740_00L
        );

        pipeline.onTradeUpdated(tradeUpdated);

        assertEquals(1, emitted.size());
        assertInstanceOf(TradeUpdated.class, emitted.get(0));
    }

    @Test
    void tradeClosedIsForwarded() {
        var tradeClosed = new TradeClosed(
                EventMetadata.root(), "TRADE-1", "SBIN", 752_00L, 2_00L, 5L, "TARGET_REACHED"
        );

        pipeline.onTradeClosed(tradeClosed);

        assertEquals(1, emitted.size());
        assertInstanceOf(TradeClosed.class, emitted.get(0));
    }

    @Test
    void nullDownstreamThrows() {
        assertThrows(NullPointerException.class, () -> new OrderPipeline(null));
    }

    @Test
    void orderCountAccumulates() {
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);

        assertEquals(0, pipeline.totalOrdersAccepted());
        pipeline.onOrderAccepted(accepted);
        assertEquals(1, pipeline.totalOrdersAccepted());
        pipeline.onOrderAccepted(accepted);
        assertEquals(2, pipeline.totalOrdersAccepted());
        pipeline.onOrderAccepted(accepted);
        assertEquals(3, pipeline.totalOrdersAccepted());
    }

    @Test
    void orderRateIsZeroInitially() {
        assertEquals(0.0, pipeline.orderRate(), "Order rate should be 0 before any order");
    }

    @Test
    void orderRateIsZeroAfterFirstOrder() {
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);
        pipeline.onOrderAccepted(accepted);
        assertEquals(0.0, pipeline.orderRate(), "Order rate should be 0 after a single order");
    }

    @Test
    void orderRateIncreasesAfterMultipleOrders() throws InterruptedException {
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);

        pipeline.onOrderAccepted(accepted);
        Thread.sleep(50);
        pipeline.onOrderAccepted(accepted);

        assertTrue(pipeline.orderRate() > 0.0,
                "Order rate should be positive after at least two orders");
    }

    @Test
    void rateAlphaIsSane() {
        assertTrue(OrderPipeline.RATE_ALPHA > 0.0 && OrderPipeline.RATE_ALPHA < 1.0,
                "Rate smoothing alpha should be between 0 and 1");
    }

    // ── MDC enrichment tests ──

    @Test
    void mdcHasCorrectValuesDuringOrderAccepted() {
        var acceptingPipeline = new OrderPipeline(e -> {
            assertEquals("OrderAccepted", MDC.get("eventType"));
            assertEquals("SBIN", MDC.get("symbol"));
            assertEquals("order", MDC.get("stage"));
        });
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);
        acceptingPipeline.onOrderAccepted(accepted);
    }

    @Test
    void mdcHasCorrectValuesDuringOrderFilled() {
        List<Trade> fills = List.of(
                new Trade("fill-1", "ORD-1", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 50, 750_00L, 1_000_000L)
        );
        var filled = new OrderFilled(EventMetadata.root(), SAMPLE_ORDER, fills);

        var verifyingPipeline = new OrderPipeline(e -> {
            assertEquals("OrderFilled", MDC.get("eventType"));
            assertEquals("order", MDC.get("stage"));
        });
        verifyingPipeline.onOrderFilled(filled);
    }

    @Test
    void mdcHasCorrectValuesDuringOrderRejected() {
        var verifyingPipeline = new OrderPipeline(e -> {
            assertEquals("OrderRejected", MDC.get("eventType"));
            assertEquals("order", MDC.get("stage"));
        });
        var rejected = new OrderRejected(EventMetadata.root(), SAMPLE_ORDER, "Insufficient margin");
        verifyingPipeline.onOrderRejected(rejected);
    }

    @Test
    void mdcHasCorrectValuesDuringTradeOpened() {
        var verifyingPipeline = new OrderPipeline(e -> {
            assertEquals("TradeOpened", MDC.get("eventType"));
            assertEquals("SBIN", MDC.get("symbol"));
            assertEquals("order", MDC.get("stage"));
        });
        var tradeOpened = new TradeOpened(
                EventMetadata.root(), "TRADE-1", "ORD-1", "sig-1", "SBIN",
                Side.BUY, 100, 750_00L, 740_00L, 770_00L
        );
        verifyingPipeline.onTradeOpened(tradeOpened);
    }

    @Test
    void mdcHasCorrectValuesDuringTradeClosed() {
        var verifyingPipeline = new OrderPipeline(e -> {
            assertEquals("TradeClosed", MDC.get("eventType"));
            assertEquals("order", MDC.get("stage"));
        });
        var tradeClosed = new TradeClosed(
                EventMetadata.root(), "TRADE-1", "SBIN", 752_00L, 2_00L, 5L, "TARGET_REACHED"
        );
        verifyingPipeline.onTradeClosed(tradeClosed);
    }
}
