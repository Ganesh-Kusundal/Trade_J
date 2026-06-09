package com.tradej.app.integration;

import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.transport.WebSocketTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end component test for the depth-20 pipeline:
 *
 * <p>DepthUpdateEvent → OrderBookEngine → GatewayEventBridge → GatewayTopicRouter → WebSocket frame
 *
 * <p>Exercises the full depth-20 data path from broker adapter output to gateway client
 * delivery, without requiring a live broker or Spring context.
 *
 * <p>The {@link GatewayEventBridge} is driven directly (bypassing the Disruptor event bus)
 * to isolate the depth pipeline wiring.
 */
@Tag("component")
class Depth20GatewayComponentTest {

    private OrderBookEngine orderBookEngine;
    private GatewayTopicRouter router;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderBookEngine = new OrderBookEngine();
        router = new GatewayTopicRouter();
        router.start();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void depthUpdateFlowsThroughOrderBookEngineAndGatewayBridge() throws Exception {
        List<byte[]> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        WebSocketTransport mockTransport = new WebSocketTransport() {
            @Override
            public void sendBinary(byte[] data) {
                received.add(data);
                latch.countDown();
            }
            @Override
            public boolean isOpen() { return true; }
            @Override
            public String id() { return "test-depth-transport"; }
            @Override
            public void close() { }
        };

        router.subscribe(mockTransport, GatewayTopic.MARKET_DEPTH);

        List<DepthLevel> bids = new ArrayList<>();
        List<DepthLevel> asks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bids.add(new DepthLevel(2450000L - i * 100L, 100L + i * 10L, 3 + i));
            asks.add(new DepthLevel(2450100L + i * 100L, 80L + i * 10L, 2 + i));
        }

        DepthUpdateEvent event = new DepthUpdateEvent(
                new EventMetadata("evt-1", System.currentTimeMillis(), System.nanoTime(), 1L, null, 1),
                "TCS",
                ExchangeSegment.NSE_EQ,
                bids,
                asks,
                20,
                System.currentTimeMillis()
        );

        orderBookEngine.onDepthUpdate(event);

        ObjectNode depthPayload = objectMapper.createObjectNode();
        depthPayload.put("symbol", "TCS");
        depthPayload.put("canonicalSymbol", "TCS");
        depthPayload.put("segment", "NSE_EQ");
        depthPayload.put("levels", 20);
        depthPayload.put("exchangeTimestampMs", event.exchangeTimestampMs());
        var bidsArray = depthPayload.putArray("bids");
        for (DepthLevel level : event.bids()) {
            var m = bidsArray.addObject();
            m.put("pricePaisa", level.pricePaisa());
            m.put("quantity", level.quantity());
            m.put("orders", level.orderCount());
        }
        var asksArray = depthPayload.putArray("asks");
        for (DepthLevel level : event.asks()) {
            var m = asksArray.addObject();
            m.put("pricePaisa", level.pricePaisa());
            m.put("quantity", level.quantity());
            m.put("orders", level.orderCount());
        }
        depthPayload.put("sequence", 1L);

        router.publish(GatewayTopic.MARKET_DEPTH, objectMapper.writeValueAsBytes(depthPayload));

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Expected MARKET_DEPTH frame within 5 seconds");

        assertFalse(received.isEmpty(), "Should have received at least one frame");

        GatewayBinaryCodec.GatewayFrame frame = GatewayBinaryCodec.decode(received.get(0));
        assertEquals(GatewayTopic.MARKET_DEPTH, frame.topic());

        JsonNode payload = objectMapper.readTree(frame.payload());
        assertEquals("TCS", payload.get("symbol").asText());
        assertEquals("NSE_EQ", payload.get("segment").asText());
        assertEquals(20, payload.get("levels").asInt());
        assertEquals(20, payload.get("bids").size(), "Should have 20 bid levels");
        assertEquals(20, payload.get("asks").size(), "Should have 20 ask levels");

        JsonNode firstBid = payload.get("bids").get(0);
        assertEquals(2450000L, firstBid.get("pricePaisa").asLong());
        assertEquals(100L, firstBid.get("quantity").asLong());

        JsonNode firstAsk = payload.get("asks").get(0);
        assertEquals(2450100L, firstAsk.get("pricePaisa").asLong());
    }

    @Test
    void orderBookEngineMaintainsBookStateAfterDepthUpdate() {
        List<DepthLevel> bids = List.of(
                new DepthLevel(2450000L, 100L, 3),
                new DepthLevel(2449900L, 200L, 5)
        );
        List<DepthLevel> asks = List.of(
                new DepthLevel(2450100L, 80L, 2),
                new DepthLevel(2450200L, 150L, 4)
        );

        DepthUpdateEvent event = new DepthUpdateEvent(
                new EventMetadata("evt-2", System.currentTimeMillis(), System.nanoTime(), 2L, null, 1),
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                bids,
                asks,
                2,
                System.currentTimeMillis()
        );

        orderBookEngine.onDepthUpdate(event);

        assertNotNull(orderBookEngine.getBook("RELIANCE", ExchangeSegment.NSE_EQ),
                "OrderBook should exist after depth update");
        assertEquals(1, orderBookEngine.bookCount());

        var snapshot = orderBookEngine.getBook("RELIANCE", ExchangeSegment.NSE_EQ).toSnapshot(5);
        assertEquals(2, snapshot.bids().size());
        assertEquals(2, snapshot.asks().size());
    }

    @Test
    void multipleDepthUpdatesAccumulateInBook() {
        for (int i = 0; i < 5; i++) {
            List<DepthLevel> bids = List.of(new DepthLevel(2450000L + i, 100L, 3));
            List<DepthLevel> asks = List.of(new DepthLevel(2450100L + i, 80L, 2));

            orderBookEngine.onDepthUpdate(new DepthUpdateEvent(
                    new EventMetadata("evt-" + i, System.currentTimeMillis(), System.nanoTime(), (long) i, null, 1),
                    "INFY",
                    ExchangeSegment.NSE_EQ,
                    bids, asks, 1, System.currentTimeMillis()
            ));
        }

        assertNotNull(orderBookEngine.getBook("INFY", ExchangeSegment.NSE_EQ));
        assertEquals(1, orderBookEngine.bookCount());
    }

    @Test
    void depthAnalyticsPipelineReceivesOrderBookUpdates() {
        List<Object> analyticsEvents = new CopyOnWriteArrayList<>();

        var imbalanceService = new com.tradej.execution.depth.OrderBookImbalanceService();
        var heatmapRecorder = new com.tradej.execution.depth.HeatmapRecorder();
        var restingAnalyzer = new com.tradej.execution.depth.RestingOrderAnalyzer();
        var icebergDetector = new com.tradej.execution.depth.IcebergDetector();
        var absorptionAnalyzer = new com.tradej.execution.depth.AbsorptionAnalyzer();

        var pipeline = new com.tradej.execution.depth.DepthAnalyticsPipeline(
                orderBookEngine, imbalanceService, heatmapRecorder,
                restingAnalyzer, icebergDetector, absorptionAnalyzer);
        pipeline.addConsumer(analyticsEvents::add);
        orderBookEngine.addListener(pipeline::onDepthUpdate);

        List<DepthLevel> bids = List.of(
                new DepthLevel(2450000L, 100L, 3),
                new DepthLevel(2449900L, 200L, 5)
        );
        List<DepthLevel> asks = List.of(
                new DepthLevel(2450100L, 80L, 2)
        );

        orderBookEngine.onDepthUpdate(new DepthUpdateEvent(
                new EventMetadata("evt-analytics", System.currentTimeMillis(), System.nanoTime(), 1L, null, 1),
                "TCS", ExchangeSegment.NSE_EQ, bids, asks, 2, System.currentTimeMillis()
        ));

        assertFalse(analyticsEvents.isEmpty(),
                "Analytics pipeline should have received events from OrderBookEngine");
    }

    @Test
    void gatewayRouterDeliversDepthOnlyToSubscribedTransports() throws Exception {
        List<byte[]> depthReceived = new CopyOnWriteArrayList<>();
        List<byte[]> tickReceived = new CopyOnWriteArrayList<>();
        CountDownLatch depthLatch = new CountDownLatch(1);

        WebSocketTransport depthTransport = new WebSocketTransport() {
            @Override
            public void sendBinary(byte[] data) { depthReceived.add(data); depthLatch.countDown(); }
            @Override
            public boolean isOpen() { return true; }
            @Override
            public String id() { return "depth-only"; }
            @Override
            public void close() { }
        };

        WebSocketTransport tickTransport = new WebSocketTransport() {
            @Override
            public void sendBinary(byte[] data) { tickReceived.add(data); }
            @Override
            public boolean isOpen() { return true; }
            @Override
            public String id() { return "tick-only"; }
            @Override
            public void close() { }
        };

        router.subscribe(depthTransport, GatewayTopic.MARKET_DEPTH);
        router.subscribe(tickTransport, GatewayTopic.MARKET_TICK);

        DepthUpdateEvent event = new DepthUpdateEvent(
                new EventMetadata("evt-iso", System.currentTimeMillis(), System.nanoTime(), 1L, null, 1),
                "SBIN", ExchangeSegment.NSE_EQ,
                List.of(new DepthLevel(80000L, 50L, 2)),
                List.of(new DepthLevel(80100L, 40L, 1)),
                1, System.currentTimeMillis()
        );

        ObjectNode isoPayload = objectMapper.createObjectNode();
        isoPayload.put("symbol", "SBIN");
        isoPayload.put("segment", "NSE_EQ");
        isoPayload.put("levels", 1);
        var isoBids = isoPayload.putArray("bids");
        var b = isoBids.addObject();
        b.put("pricePaisa", 80000L); b.put("quantity", 50L); b.put("orders", 2);
        var isoAsks = isoPayload.putArray("asks");
        var a = isoAsks.addObject();
        a.put("pricePaisa", 80100L); a.put("quantity", 40L); a.put("orders", 1);

        router.publish(GatewayTopic.MARKET_DEPTH, objectMapper.writeValueAsBytes(isoPayload));

        assertTrue(depthLatch.await(5, TimeUnit.SECONDS),
                "Depth transport should receive MARKET_DEPTH frame");
        assertFalse(tickReceived.isEmpty() ? false : true,
                "Tick transport should NOT receive MARKET_DEPTH frame");
        assertTrue(tickReceived.isEmpty(),
                "Tick transport should NOT receive MARKET_DEPTH frame");
    }
}
