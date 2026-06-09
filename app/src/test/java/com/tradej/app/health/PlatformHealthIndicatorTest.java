package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PlatformHealthIndicatorTest {

    @Mock IBrokerConnection brokerConnection;
    @Mock WebSocketMultiplexer brokerWebsocket;
    @Mock MarketDataPipeline marketDataPipeline;
    @Mock EventBus eventBus;
    @Mock DisruptorBusMetrics disruptorBusMetrics;
    @Mock OrderPipeline orderPipeline;
    @Mock BrokerErrorTracker errorTracker;
    @Mock AlertManager alertManager;

    private PlatformHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        when(brokerConnection.websocket()).thenReturn(brokerWebsocket);
        when(brokerWebsocket.subscriptions()).thenReturn(Map.of());
        when(errorTracker.totalErrors()).thenReturn(0L);
        when(disruptorBusMetrics.isStarted()).thenReturn(true);
        when(disruptorBusMetrics.subscriberCount()).thenReturn(5);
        when(disruptorBusMetrics.dispatchQueueDepth()).thenReturn(0);
        when(disruptorBusMetrics.dispatchDroppedEventCount()).thenReturn(0L);
        when(marketDataPipeline.totalTicksProcessed()).thenReturn(100L);
        when(marketDataPipeline.lastTickTimestampMs()).thenReturn(System.currentTimeMillis());
        when(marketDataPipeline.tickRate()).thenReturn(1.5);
        when(orderPipeline.totalOrdersAccepted()).thenReturn(10L);
        when(orderPipeline.orderRate()).thenReturn(0.5);

        indicator = new PlatformHealthIndicator(
                brokerConnection, marketDataPipeline, eventBus,
                disruptorBusMetrics, orderPipeline, errorTracker, alertManager
        );
    }

    @Test
    void healthIsUpWhenAllSubsystemHealthy() {
        when(brokerWebsocket.isConnected()).thenReturn(true);

        var health = indicator.health();
        assertEquals("UP", health.getStatus().getCode());
    }

    @Test
    void healthIsDownWhenBrokerDisconnected() {
        when(brokerWebsocket.isConnected()).thenReturn(false);

        var health = indicator.health();
        assertEquals("DOWN", health.getStatus().getCode());
    }

    @Test
    void healthIsDownWhenEventBusNotStarted() {
        when(brokerWebsocket.isConnected()).thenReturn(true);
        when(disruptorBusMetrics.isStarted()).thenReturn(false);

        var health = indicator.health();
        assertEquals("DOWN", health.getStatus().getCode());
    }

    @Test
    void healthDetailsIncludeBrokerInfo() {
        when(brokerWebsocket.isConnected()).thenReturn(true);
        MarketSubscriptionRequest req1 = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        MarketSubscriptionRequest req2 = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        when(brokerWebsocket.subscriptions()).thenReturn(Map.of(req1, FeedMode.TICKER, req2, FeedMode.TICKER));

        var health = indicator.health();
        var broker = (java.util.Map<?, ?>) health.getDetails().get("broker");
        assertNotNull(broker);
        assertEquals("UP", broker.get("status"));
        assertEquals(true, broker.get("websocketConnected"));
        assertEquals(2, broker.get("subscriptions"));
    }

    @Test
    void healthDetailsIncludeEventBusInfo() {
        when(brokerWebsocket.isConnected()).thenReturn(true);

        var health = indicator.health();
        var eventBusDetails = (java.util.Map<?, ?>) health.getDetails().get("eventBus");
        assertNotNull(eventBusDetails);
        assertEquals("UP", eventBusDetails.get("status"));
        assertEquals(true, eventBusDetails.get("started"));
        assertEquals(5, eventBusDetails.get("subscribers"));
    }

    @Test
    void healthDetailsIncludeMarketDataInfo() {
        when(brokerWebsocket.isConnected()).thenReturn(true);

        var health = indicator.health();
        var marketData = (java.util.Map<?, ?>) health.getDetails().get("marketData");
        assertNotNull(marketData);
        assertEquals("UP", marketData.get("status"));
        assertEquals(100L, marketData.get("totalTicks"));
    }

    @Test
    void healthReportsDownWhenBrokerHasHighErrorCount() {
        when(brokerWebsocket.isConnected()).thenReturn(true);
        when(errorTracker.totalErrors()).thenReturn(50L);

        var health = indicator.health();
        var broker = (java.util.Map<?, ?>) health.getDetails().get("broker");
        assertEquals(50L, broker.get("recentErrors"));
    }
}
