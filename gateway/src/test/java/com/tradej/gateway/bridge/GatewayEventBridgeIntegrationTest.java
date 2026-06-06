package com.tradej.gateway.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.transport.WebSocketTransport;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("integration")
class GatewayEventBridgeIntegrationTest {

    private GatewayTopicRouter router;
    private GatewayEventBridge bridge;
    private GatewayWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        router = new GatewayTopicRouter();
        bridge = new GatewayEventBridge(router, objectMapper);
        handler = new GatewayWebSocketHandler(router);
    }

    @AfterEach
    void tearDown() {
        bridge.close();
        router.stop();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static WebSocketTransport openTransport(String id) {
        WebSocketTransport transport = mock(WebSocketTransport.class);
        when(transport.id()).thenReturn(id);
        when(transport.isOpen()).thenReturn(true);
        return transport;
    }

    private static boolean awaitSent(GatewayTopicRouter router, long target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (router.sentEventCount() < target && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return router.sentEventCount() >= target;
    }

    private static GatewayBinaryCodec.GatewayFrame decodeSentFrame(WebSocketTransport transport)
            throws Exception {
        var captor = ArgumentCaptor.forClass(byte[].class);
        verify(transport).sendBinary(captor.capture());
        return GatewayBinaryCodec.decode(captor.getValue());
    }

    private static MarketTickEvent sampleTick(String symbol) {
        return new MarketTickEvent(
                EventMetadata.root(), 1L, symbol,
                ExchangeSegment.NSE_EQ, FeedMode.FULL,
                750_00L, 100L, 50_000L,
                System.currentTimeMillis(), Optional.empty(), 0L, 0L);
    }

    // ── End-to-end event flow ───────────────────────────────────────────

    @Test
    void eventFlowsFromBridgeThroughRouterToTransport() throws Exception {
        WebSocketTransport transport = openTransport("s1");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        var event = new PnlUpdatedEvent(
                EventMetadata.root(), 1000L, 500L, 2000L);
        bridge.onDomainEvent(event);

        assertTrue(awaitSent(router, 1, 5000),
                "Timeout waiting for event delivery");

        GatewayBinaryCodec.GatewayFrame frame = decodeSentFrame(transport);
        assertEquals(GatewayTopic.PNL_UPDATE, frame.topic(),
                "Frame should be routed to PNL_UPDATE");
        assertTrue(frame.sequence() > 0, "Frame should have a positive sequence number");
        assertTrue(frame.payload().length > 0, "Frame should contain JSON payload");

        String json = new String(frame.payload());
        assertTrue(json.contains("1000"), "Payload should contain realizedPnlPaisa");
        assertTrue(json.contains("\"realizedPnlPaisa\""), "Payload should include realizedPnlPaisa key");
    }

    @Test
    void marketTickEventRoutedToMarketTickTopic() throws Exception {
        WebSocketTransport transport = openTransport("s1");
        router.subscribe(transport, GatewayTopic.MARKET_TICK);
        router.start();

        bridge.onDomainEvent(sampleTick("SBIN"));

        assertTrue(awaitSent(router, 1, 5000),
                "Timeout waiting for tick delivery");

        GatewayBinaryCodec.GatewayFrame frame = decodeSentFrame(transport);
        assertEquals(GatewayTopic.MARKET_TICK, frame.topic(),
                "MarketTickEvent should route to MARKET_TICK");

        String json = new String(frame.payload());
        assertTrue(json.contains("SBIN"), "Payload should contain symbol");
        assertTrue(json.contains("\"ltpPaisa\""), "Payload should include ltpPaisa key");
    }

    @Test
    void unknownEventTypeIsSilentlyDropped() throws Exception {
        WebSocketTransport transport = openTransport("s1");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        var unhandled = new DomainEvent() {
            @Override
            public EventMetadata metadata() {
                return EventMetadata.root();
            }

            @Override
            public void accept(DomainEventVisitor visitor) {
                // No-op for test event
            }
        };
        bridge.onDomainEvent(unhandled);

        Thread.sleep(200);
        router.stop();

        verify(transport, never()).sendBinary(any(byte[].class));
        assertEquals(0, router.sentEventCount(),
                "No events should be sent for unhandled types");
    }

    // ── Dedup through the full pipeline ─────────────────────────────────

    @Test
    void duplicateEventsAreDeduplicatedThroughFullPipeline() throws Exception {
        WebSocketTransport transport = openTransport("s1");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        String fixedEventId = "dedup-test-id";
        var metadata = new EventMetadata(fixedEventId, 0L, 0L, 0L, "", 1);
        var event = new PnlUpdatedEvent(metadata, 100L, 50L, 200L);

        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);

        assertTrue(awaitSent(router, 1, 5000),
                "Only one copy should be delivered despite 3 publishes");

        verify(transport, times(1)).sendBinary(any(byte[].class));
        assertEquals(1, bridge.eventCount(),
                "Bridge should report 1 processed event");
        assertEquals(2, bridge.dedupHitCount(),
                "Bridge should report 2 dedup hits");
    }

    @Test
    void uniqueEventsAreNotDeduplicated() throws Exception {
        WebSocketTransport transport = openTransport("s1");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 200L, 100L, 400L));

        assertTrue(awaitSent(router, 2, 5000),
                "Two unique events should both be delivered");

        verify(transport, times(2)).sendBinary(any(byte[].class));
        assertEquals(2, bridge.eventCount());
        assertEquals(0, bridge.dedupHitCount());
    }

    // ── Connection lifecycle ────────────────────────────────────────────

    @Test
    void subscribeThenUnsubscribeStopsEventDelivery() throws Exception {
        WebSocketTransport transport = openTransport("s1");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        assertTrue(awaitSent(router, 1, 5000),
                "First event should be delivered");

        router.unsubscribeAll(transport);

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 200L, 100L, 400L));
        Thread.sleep(200);
        router.stop();

        verify(transport, times(1)).sendBinary(any(byte[].class));
        assertEquals(1, router.sentEventCount(),
                "Only the pre-unsubscribe event should have been sent");
    }

    // ── Multiple transports, different topics ───────────────────────────

    @Test
    void multipleTransportsReceiveOnlyTheirSubscribedTopics() throws Exception {
        WebSocketTransport pnlTransport = openTransport("pnl-subscriber");
        WebSocketTransport tickTransport = openTransport("tick-subscriber");
        router.subscribe(pnlTransport, GatewayTopic.PNL_UPDATE);
        router.subscribe(tickTransport, GatewayTopic.MARKET_TICK);
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(sampleTick("SBIN"));

        assertTrue(awaitSent(router, 2, 5000),
                "Both events should be delivered (one per transport)");

        verify(pnlTransport, times(1)).sendBinary(any(byte[].class));
        verify(tickTransport, times(1)).sendBinary(any(byte[].class));

        {
            var captor = ArgumentCaptor.forClass(byte[].class);
            verify(pnlTransport).sendBinary(captor.capture());
            var frame = GatewayBinaryCodec.decode(captor.getValue());
            assertEquals(GatewayTopic.PNL_UPDATE, frame.topic());
        }

        {
            var captor = ArgumentCaptor.forClass(byte[].class);
            verify(tickTransport).sendBinary(captor.capture());
            var frame = GatewayBinaryCodec.decode(captor.getValue());
            assertEquals(GatewayTopic.MARKET_TICK, frame.topic());
        }
    }

    @Test
    void sameTransportSubscribedToMultipleTopicsReceivesAll() throws Exception {
        WebSocketTransport transport = openTransport("multi-topic");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.subscribe(transport, GatewayTopic.MARKET_TICK);
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(sampleTick("SBIN"));

        assertTrue(awaitSent(router, 2, 5000),
                "Transport subscribed to two topics should receive both events");

        verify(transport, times(2)).sendBinary(any(byte[].class));
    }

    // ── Backpressure integration ────────────────────────────────────────

    @Test
    void eventsUpToQueueCapacityAllDelivered() throws Exception {
        WebSocketTransport transport = openTransport("capacity-test");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        int count = 50;
        for (int i = 0; i < count; i++) {
            bridge.onDomainEvent(
                    new PnlUpdatedEvent(EventMetadata.root(), i * 100L, 0L, 0L));
        }

        assertTrue(awaitSent(router, count, 5000),
                "All " + count + " events should be delivered");
        assertEquals(0, router.droppedEventCount(),
                "No drops when queue has capacity");
        verify(transport, times(count)).sendBinary(any(byte[].class));
    }

    // ── Bridge metrics through integration ──────────────────────────────

    @Test
    void bridgeMetricsReflectFullPipelineActivity() {
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 200L, 100L, 400L));
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 300L, 150L, 600L));

        assertEquals(3, bridge.eventCount(),
                "Bridge should count 3 processed events");
        assertEquals(3, bridge.dedupCacheSize(),
                "Dedup cache should have 3 entries");
        assertEquals(0, bridge.dedupHitCount(),
                "No dedup hits for unique events");
    }

    @Test
    void bridgeMetricsWithDedup() {
        router.start();

        String commonId = "duplicate-id";
        var meta = new EventMetadata(commonId, 0L, 0L, 0L, "", 1);

        bridge.onDomainEvent(new PnlUpdatedEvent(meta, 100L, 50L, 200L));
        bridge.onDomainEvent(new PnlUpdatedEvent(meta, 100L, 50L, 200L));
        bridge.onDomainEvent(new PnlUpdatedEvent(meta, 100L, 50L, 200L));

        assertEquals(1, bridge.eventCount());
        assertEquals(2, bridge.dedupHitCount());
        assertEquals(1, bridge.dedupCacheSize());
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    void restartRouterAfterStopContinuesDelivery() throws Exception {
        WebSocketTransport transport = openTransport("restart-test");
        router.subscribe(transport, GatewayTopic.PNL_UPDATE);
        router.start();

        bridge.onDomainEvent(new PnlUpdatedEvent(EventMetadata.root(), 1L, 0L, 0L));
        assertTrue(awaitSent(router, 1, 5000), "First event delivered");
        router.stop();

        router.start();

        bridge.onDomainEvent(new PnlUpdatedEvent(EventMetadata.root(), 2L, 0L, 0L));
        assertTrue(awaitSent(router, 2, 5000),
                "Second event should be delivered after restart");

        verify(transport, times(2)).sendBinary(any(byte[].class));
    }

    @Test
    void transportExceptionDuringPublishDoesNotBlockOthers() throws Exception {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        WebSocketTransport liveTransport = mock(WebSocketTransport.class);
        when(liveTransport.id()).thenReturn("live");
        when(liveTransport.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add("live");
            latch.countDown();
            return null;
        }).when(liveTransport).sendBinary(any(byte[].class));

        WebSocketTransport failingTransport = mock(WebSocketTransport.class);
        when(failingTransport.id()).thenReturn("failing");
        when(failingTransport.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add("failing");
            latch.countDown();
            throw new java.io.IOException("Connection reset");
        }).when(failingTransport).sendBinary(any(byte[].class));

        WebSocketTransport thirdTransport = mock(WebSocketTransport.class);
        when(thirdTransport.id()).thenReturn("third");
        when(thirdTransport.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add("third");
            latch.countDown();
            return null;
        }).when(thirdTransport).sendBinary(any(byte[].class));

        router.subscribe(liveTransport, GatewayTopic.PNL_UPDATE);
        router.subscribe(failingTransport, GatewayTopic.PNL_UPDATE);
        router.subscribe(thirdTransport, GatewayTopic.PNL_UPDATE);
        router.start();

        bridge.onDomainEvent(new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "All 3 transports should have attempted send");
        router.stop();

        assertTrue(received.contains("live"), "Live transport should have received");
        assertTrue(received.contains("failing"), "Failing transport should have attempted send");
        assertTrue(received.contains("third"), "Third transport should have received");
        verify(liveTransport).sendBinary(any(byte[].class));
        verify(failingTransport).sendBinary(any(byte[].class));
        verify(thirdTransport).sendBinary(any(byte[].class));
    }
}
