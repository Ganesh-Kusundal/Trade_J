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
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the gateway event delivery pipeline.
 *
 * <p>Wires together real instances of {@link GatewayEventBridge},
 * {@link GatewayTopicRouter}, and {@link GatewayWebSocketHandler}
 * to verify end-to-end flows: domain event ingestion, dedup, topic
 * routing, session subscription, connection lifecycle, and binary
 * frame delivery over mocked {@link WebSocketSession}s.
 */
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

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    /** Await {@link GatewayTopicRouter#sentEventCount()} to reach target. */
    private static boolean awaitSent(GatewayTopicRouter router, long target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (router.sentEventCount() < target && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return router.sentEventCount() >= target;
    }

    /** Decode the BinaryMessage payload sent to a session into a GatewayFrame. */
    private static GatewayBinaryCodec.GatewayFrame decodeSentFrame(WebSocketSession session)
            throws Exception {
        var captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        byte[] raw = new byte[captor.getValue().getPayload().remaining()];
        captor.getValue().getPayload().get(raw);
        return GatewayBinaryCodec.decode(raw);
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
    void eventFlowsFromBridgeThroughRouterToSession() throws Exception {
        WebSocketSession session = openSession("s1");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        var event = new PnlUpdatedEvent(
                EventMetadata.root(), 1000L, 500L, 2000L);
        bridge.onDomainEvent(event);

        assertTrue(awaitSent(router, 1, 5000),
                "Timeout waiting for event delivery");

        GatewayBinaryCodec.GatewayFrame frame = decodeSentFrame(session);
        assertEquals(GatewayTopic.PNL_UPDATE, frame.topic(),
                "Frame should be routed to PNL_UPDATE");
        assertTrue(frame.sequence() > 0, "Frame should have a positive sequence number");
        assertTrue(frame.payload().length > 0, "Frame should contain JSON payload");

        // Verify payload contains expected fields
        String json = new String(frame.payload());
        assertTrue(json.contains("1000"), "Payload should contain realizedPnlPaisa");
        assertTrue(json.contains("\"realizedPnlPaisa\""), "Payload should include realizedPnlPaisa key");
    }

    @Test
    void marketTickEventRoutedToMarketTickTopic() throws Exception {
        WebSocketSession session = openSession("s1");
        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.start();

        bridge.onDomainEvent(sampleTick("SBIN"));

        assertTrue(awaitSent(router, 1, 5000),
                "Timeout waiting for tick delivery");

        GatewayBinaryCodec.GatewayFrame frame = decodeSentFrame(session);
        assertEquals(GatewayTopic.MARKET_TICK, frame.topic(),
                "MarketTickEvent should route to MARKET_TICK");

        String json = new String(frame.payload());
        assertTrue(json.contains("SBIN"), "Payload should contain symbol");
        assertTrue(json.contains("\"ltpPaisa\""), "Payload should include ltpPaisa key");
    }

    @Test
    void unknownEventTypeIsSilentlyDropped() throws Exception {
        WebSocketSession session = openSession("s1");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        // A DomainEvent type not handled in the bridge switch
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

        Thread.sleep(200); // give publisher thread time to NOT deliver
        router.stop();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
        assertEquals(0, router.sentEventCount(),
                "No events should be sent for unhandled types");
    }

    // ── Dedup through the full pipeline ─────────────────────────────────

    @Test
    void duplicateEventsAreDeduplicatedThroughFullPipeline() throws Exception {
        WebSocketSession session = openSession("s1");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        // Same event ID — use a fixed eventId
        String fixedEventId = "dedup-test-id";
        var metadata = new EventMetadata(fixedEventId, 0L, 0L, 0L, "", 1);
        var event = new PnlUpdatedEvent(metadata, 100L, 50L, 200L);

        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);

        assertTrue(awaitSent(router, 1, 5000),
                "Only one copy should be delivered despite 3 publishes");

        verify(session, times(1)).sendMessage(any(BinaryMessage.class));
        assertEquals(1, bridge.eventCount(),
                "Bridge should report 1 processed event");
        assertEquals(2, bridge.dedupHitCount(),
                "Bridge should report 2 dedup hits");
    }

    @Test
    void uniqueEventsAreNotDeduplicated() throws Exception {
        WebSocketSession session = openSession("s1");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 200L, 100L, 400L));

        assertTrue(awaitSent(router, 2, 5000),
                "Two unique events should both be delivered");

        verify(session, times(2)).sendMessage(any(BinaryMessage.class));
        assertEquals(2, bridge.eventCount());
        assertEquals(0, bridge.dedupHitCount());
    }

    // ── Connection lifecycle ────────────────────────────────────────────

    @Test
    void subscribeThenCloseConnectionStopsEventDelivery() throws Exception {
        WebSocketSession session = openSession("s1");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        // First event — delivered
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        assertTrue(awaitSent(router, 1, 5000),
                "First event should be delivered");

        // Disconnect via handler — removes session from all subscriptions
        handler.afterConnectionClosed(session,
                new org.springframework.web.socket.CloseStatus(1000, "Normal"));

        // Second event — should NOT be delivered (session unsubscribed)
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 200L, 100L, 400L));
        Thread.sleep(200);
        router.stop();

        // Session should only have received the first message
        verify(session, times(1)).sendMessage(any(BinaryMessage.class));
        assertEquals(1, router.sentEventCount(),
                "Only the pre-disconnect event should have been sent");
    }

    @Test
    void afterConnectionEstablishedPublishesHealthEvent() throws Exception {
        WebSocketSession session = openSession("health-check");
        router.subscribe(session, GatewayTopic.PIPELINE_HEALTH);
        router.start();

        handler.afterConnectionEstablished(session);

        assertTrue(awaitSent(router, 1, 5000),
                "Health event should be sent on connection");

        verify(session).sendMessage(any(BinaryMessage.class));
        assertEquals(1, router.sentEventCount(),
                "Router should report 1 health event sent");
    }

    @Test
    void multipleConnectionsAllReceiveHealthEvents() throws Exception {
        WebSocketSession s1 = openSession("client-1");
        WebSocketSession s2 = openSession("client-2");
        router.subscribe(s1, GatewayTopic.PIPELINE_HEALTH);
        router.subscribe(s2, GatewayTopic.PIPELINE_HEALTH);
        router.start();

        handler.afterConnectionEstablished(s1);
        handler.afterConnectionEstablished(s2);

        assertTrue(awaitSent(router, 2, 5000),
                "Both health events should be sent");

        verify(s1).sendMessage(any(BinaryMessage.class));
        verify(s2).sendMessage(any(BinaryMessage.class));
    }

    // ── Multiple sessions, different topics ─────────────────────────────

    @Test
    void multipleSessionsReceiveOnlyTheirSubscribedTopics() throws Exception {
        WebSocketSession pnlSession = openSession("pnl-subscriber");
        WebSocketSession tickSession = openSession("tick-subscriber");
        router.subscribe(pnlSession, GatewayTopic.PNL_UPDATE);
        router.subscribe(tickSession, GatewayTopic.MARKET_TICK);
        router.start();

        // Push events for both topics
        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(sampleTick("SBIN"));

        assertTrue(awaitSent(router, 2, 5000),
                "Both events should be delivered (one per session)");

        // Each session should have received exactly one message
        verify(pnlSession, times(1)).sendMessage(any(BinaryMessage.class));
        verify(tickSession, times(1)).sendMessage(any(BinaryMessage.class));

        // PNL session should have received PNL frame
        {
            var captor = ArgumentCaptor.forClass(BinaryMessage.class);
            verify(pnlSession).sendMessage(captor.capture());
            byte[] raw = new byte[captor.getValue().getPayload().remaining()];
            captor.getValue().getPayload().get(raw);
            var frame = GatewayBinaryCodec.decode(raw);
            assertEquals(GatewayTopic.PNL_UPDATE, frame.topic());
        }

        // Tick session should have received MARKET_TICK frame
        {
            var captor = ArgumentCaptor.forClass(BinaryMessage.class);
            verify(tickSession).sendMessage(captor.capture());
            byte[] raw = new byte[captor.getValue().getPayload().remaining()];
            captor.getValue().getPayload().get(raw);
            var frame = GatewayBinaryCodec.decode(raw);
            assertEquals(GatewayTopic.MARKET_TICK, frame.topic());
        }
    }

    @Test
    void sameSessionSubscribedToMultipleTopicsReceivesAll() throws Exception {
        WebSocketSession session = openSession("multi-topic");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.start();

        bridge.onDomainEvent(
                new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));
        bridge.onDomainEvent(sampleTick("SBIN"));

        assertTrue(awaitSent(router, 2, 5000),
                "Session subscribed to two topics should receive both events");

        verify(session, times(2)).sendMessage(any(BinaryMessage.class));
    }

    // ── Backpressure integration ────────────────────────────────────────

    @Test
    void eventsUpToQueueCapacityAllDelivered() throws Exception {
        WebSocketSession session = openSession("capacity-test");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        // Default queue capacity is 1024 — send 50 events as a smoke test
        int count = 50;
        for (int i = 0; i < count; i++) {
            bridge.onDomainEvent(
                    new PnlUpdatedEvent(EventMetadata.root(), i * 100L, 0L, 0L));
        }

        assertTrue(awaitSent(router, count, 5000),
                "All " + count + " events should be delivered");
        assertEquals(0, router.droppedEventCount(),
                "No drops when queue has capacity");
        verify(session, times(count)).sendMessage(any(BinaryMessage.class));
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
        WebSocketSession session = openSession("restart-test");
        router.subscribe(session, GatewayTopic.PNL_UPDATE);
        router.start();

        // First batch — delivered
        bridge.onDomainEvent(new PnlUpdatedEvent(EventMetadata.root(), 1L, 0L, 0L));
        assertTrue(awaitSent(router, 1, 5000), "First event delivered");
        router.stop();

        // Restart — same router instance
        router.start();

        // Second batch — should be delivered after restart
        bridge.onDomainEvent(new PnlUpdatedEvent(EventMetadata.root(), 2L, 0L, 0L));
        assertTrue(awaitSent(router, 2, 5000),
                "Second event should be delivered after restart");

        verify(session, times(2)).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void sessionIOExceptionDuringPublishDoesNotBlockOthers() throws Exception {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        WebSocketSession liveSession = mock(WebSocketSession.class);
        when(liveSession.getId()).thenReturn("live");
        when(liveSession.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add("live");
            latch.countDown();
            return null;
        }).when(liveSession).sendMessage(any(BinaryMessage.class));

        WebSocketSession failingSession = mock(WebSocketSession.class);
        when(failingSession.getId()).thenReturn("failing");
        when(failingSession.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add("failing");
            latch.countDown();
            throw new java.io.IOException("Connection reset");
        }).when(failingSession).sendMessage(any(BinaryMessage.class));

        WebSocketSession thirdSession = mock(WebSocketSession.class);
        when(thirdSession.getId()).thenReturn("third");
        when(thirdSession.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add("third");
            latch.countDown();
            return null;
        }).when(thirdSession).sendMessage(any(BinaryMessage.class));

        router.subscribe(liveSession, GatewayTopic.PNL_UPDATE);
        router.subscribe(failingSession, GatewayTopic.PNL_UPDATE);
        router.subscribe(thirdSession, GatewayTopic.PNL_UPDATE);
        router.start();

        bridge.onDomainEvent(new PnlUpdatedEvent(EventMetadata.root(), 100L, 50L, 200L));

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "All 3 sessions should have attempted send");
        router.stop();

        assertTrue(received.contains("live"), "Live session should have received");
        assertTrue(received.contains("failing"), "Failing session should have attempted send");
        assertTrue(received.contains("third"), "Third session should have received");
        verify(liveSession).sendMessage(any(BinaryMessage.class));
        verify(failingSession).sendMessage(any(BinaryMessage.class));
        verify(thirdSession).sendMessage(any(BinaryMessage.class));
    }
}
