package com.tradej.app.health;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.hotpath.OrderPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderPipelineHealthIndicatorTest {

    private OrderPipeline pipeline;
    private OrderPipelineHealthIndicator indicator;

    private static final Order SAMPLE_ORDER = new Order(
            "ORD-1", "sig-1", "SBIN", ExchangeSegment.NSE_EQ,
            Side.BUY, ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.OPEN,
            100, 0, 750_00L, 0L, 1_000_000L, ""
    );

    @BeforeEach
    void setUp() {
        pipeline = new OrderPipeline(e -> { });
        indicator = new OrderPipelineHealthIndicator(pipeline);
    }

    @Test
    void healthIsUpEvenWhenNoEventsProcessed() {
        // The order pipeline is healthy even when idle — it's ready to accept events
        var health = indicator.health();
        assertEquals("UP", health.getStatus().getCode());
    }

    @Test
    void healthReportsZeroCountsWhenIdle() {
        var health = indicator.health();
        assertEquals(0L, health.getDetails().get("totalOrdersAccepted"));
        assertFalse((Boolean) health.getDetails().get("hasProcessedOrders"));
    }

    @Test
    void healthReportsOrderCountsAfterProcessing() {
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);
        pipeline.onOrderAccepted(accepted);
        pipeline.onOrderAccepted(accepted);
        pipeline.onOrderAccepted(accepted);

        var health = indicator.health();
        assertEquals(3L, health.getDetails().get("totalOrdersAccepted"));
        assertTrue((Boolean) health.getDetails().get("hasProcessedOrders"));
    }

    @Test
    void healthReportsOrderRate() {
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);

        pipeline.onOrderAccepted(accepted);
        pipeline.onOrderAccepted(accepted);

        var health = indicator.health();
        assertTrue((Double) health.getDetails().get("orderRate") >= 0.0);
    }

    @Test
    void healthIsAlwaysUp() {
        // The pipeline is always considered healthy — it's a passive component
        // that is ready to process events in any state
        assertEquals("UP", indicator.health().getStatus().getCode());

        // After events
        var accepted = new OrderAccepted(EventMetadata.root(), SAMPLE_ORDER);
        pipeline.onOrderAccepted(accepted);
        assertEquals("UP", indicator.health().getStatus().getCode());

        // Always UP
        pipeline.onOrderAccepted(accepted);
        assertEquals("UP", indicator.health().getStatus().getCode());
    }
}
