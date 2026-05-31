package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.Answers;

/**
 * Unit tests for {@link GatewayTopicRouter}.
 *
 * <p>Covers async backpressure, lifecycle, subscribe/unsubscribe, publish,
 * publishFiltered, concurrency, shutdown drain, error handling, and metrics.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GatewayTopicRouterTest {

    private static final int SMALL_QUEUE = 4;
    private static final int NORMAL_QUEUE = 1024;

    private GatewayTopicRouter router;

    @BeforeEach
    void setUp() {
        router = null;
    }

    @AfterEach
    void tearDown() {
        if (router != null) {
            router.stop();
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Test
    void startAndStopWorkCorrectly() {
        router = new GatewayTopicRouter(SMALL_QUEUE);
        router.start();
        router.stop();
    }

    @Test
    void startIsIdempotent() {
        router = new GatewayTopicRouter(SMALL_QUEUE);
        router.start();
        router.start();
        router.stop();
    }

    @Test
    void stopWithoutStartDoesNotThrow() {
        router = new GatewayTopicRouter(SMALL_QUEUE);
        router.stop();
    }

    // ── Subscribe / Unsubscribe ─────────────────────────────────────────

    @Test
    void subscribeAddsSessionToTopic() {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        assertEquals(1, router.subscriberCount(GatewayTopic.MARKET_TICK));
    }

    @Test
    void subscribeMultipleTopics() {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.subscribe(session, GatewayTopic.MARKET_DEPTH);
        router.subscribe(session, GatewayTopic.CANDLE_CLOSED);

        assertEquals(1, router.subscriberCount(GatewayTopic.MARKET_TICK));
        assertEquals(1, router.subscriberCount(GatewayTopic.MARKET_DEPTH));
        assertEquals(1, router.subscriberCount(GatewayTopic.CANDLE_CLOSED));
    }

    @Test
    void subscribeSameSessionTwiceIsIdempotent() {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.subscribe(session, GatewayTopic.MARKET_TICK);

        assertEquals(1, router.subscriberCount(GatewayTopic.MARKET_TICK));
    }

    @Test
    void unsubscribeAllRemovesSessionFromAllTopics() {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.subscribe(session, GatewayTopic.MARKET_DEPTH);
        router.subscribe(session, GatewayTopic.CANDLE_CLOSED);

        // Verify subscribed before unsubscribe
        assertEquals(1, router.subscriberCount(GatewayTopic.MARKET_TICK));

        router.unsubscribeAll(session);

        assertEquals(0, router.subscriberCount(GatewayTopic.MARKET_TICK));
        assertEquals(0, router.subscriberCount(GatewayTopic.MARKET_DEPTH));
        assertEquals(0, router.subscriberCount(GatewayTopic.CANDLE_CLOSED));
    }

    @Test
    void unsubscribeAllForUnknownSessionDoesNothing() {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("unknown");

        router.unsubscribeAll(session);
    }

    @Test
    void subscriberCountReturnsZeroForUnsubscribedTopic() {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        assertEquals(0, router.subscriberCount(GatewayTopic.SCAN_COMPLETED));
    }

    // ── Publish ─────────────────────────────────────────────────────────

    @Test
    void publishSendsToSubscribedSession() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = openSession("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.start();

        router.publish(GatewayTopic.MARKET_TICK, "{\"price\":100}".getBytes());

        assertTrue(awaitSentCount(router, 1, 3000), "Timeout waiting for sent events");
        router.stop();

        verify(session).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void publishSendsToAllSubscribedSessions() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession s1 = openSession("s1");
        WebSocketSession s2 = openSession("s2");
        WebSocketSession s3 = openSession("s3");

        router.subscribe(s1, GatewayTopic.MARKET_TICK);
        router.subscribe(s2, GatewayTopic.MARKET_TICK);
        router.subscribe(s3, GatewayTopic.MARKET_TICK);

        router.start();
        router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());

        assertTrue(awaitSentCount(router, 3, 3000), "Timeout waiting for sent events");
        router.stop();

        verify(s1).sendMessage(any(BinaryMessage.class));
        verify(s2).sendMessage(any(BinaryMessage.class));
        verify(s3).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void publishToTopicWithNoSubscribersDoesNothing() throws Exception {
        router = new GatewayTopicRouter(SMALL_QUEUE);
        // Only stub getId() — isOpen() is never called since session is
        // subscribed to MARKET_TICK, not MARKET_DEPTH
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.start();

        router.publish(GatewayTopic.MARKET_DEPTH, "data".getBytes());

        Thread.sleep(200);
        router.stop();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void publishEncodesFrameWithHeader() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = openSession("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.start();

        byte[] payload = "hello".getBytes();
        router.publish(GatewayTopic.MARKET_TICK, payload);

        assertTrue(awaitSentCount(router, 1, 3000), "Timeout waiting for sent events");
        router.stop();

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());

        byte[] sentData = new byte[captor.getValue().getPayload().remaining()];
        captor.getValue().getPayload().get(sentData);

        var frame = GatewayBinaryCodec.decode(sentData);
        assertEquals(GatewayTopic.MARKET_TICK, frame.topic());
        assertTrue(frame.sequence() > 0);
        assertArrayEquals(payload, frame.payload());
    }

    // ── publishFiltered ─────────────────────────────────────────────────

    @Test
    void publishFilteredOnlySendsToMatchingSessions() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession s1 = openSession("session-A");
        WebSocketSession s2 = openSession("session-B");

        router.subscribe(s1, GatewayTopic.MARKET_TICK);
        router.subscribe(s2, GatewayTopic.MARKET_TICK);

        router.start();
        router.publishFiltered(GatewayTopic.MARKET_TICK, "data".getBytes(),
                id -> id.equals("session-A"));

        assertTrue(awaitSentCount(router, 1, 3000), "Timeout waiting for sent events");
        router.stop();

        verify(s1).sendMessage(any(BinaryMessage.class));
        verify(s2, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void publishFilteredWithNullFilterSendsToAll() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession s1 = openSession("s1");
        WebSocketSession s2 = openSession("s2");

        router.subscribe(s1, GatewayTopic.MARKET_TICK);
        router.subscribe(s2, GatewayTopic.MARKET_TICK);

        router.start();
        router.publishFiltered(GatewayTopic.MARKET_TICK, "data".getBytes(), null);

        assertTrue(awaitSentCount(router, 2, 3000), "Timeout waiting for sent events");
        router.stop();

        verify(s1).sendMessage(any(BinaryMessage.class));
        verify(s2).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void publishFilteredWithEmptyFilterSendsToNone() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = openSession("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);

        router.start();
        router.publishFiltered(GatewayTopic.MARKET_TICK, "data".getBytes(),
                id -> false);

        Thread.sleep(200);
        router.stop();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
        assertEquals(0, router.sentEventCount());
    }

    // ── Backpressure ────────────────────────────────────────────────────

    @Test
    void backpressureDropsEventsWhenQueueIsFull() {
        router = new GatewayTopicRouter(SMALL_QUEUE);

        for (int i = 0; i < SMALL_QUEUE; i++) {
            router.publish(GatewayTopic.MARKET_TICK, ("data-" + i).getBytes());
        }

        router.publish(GatewayTopic.MARKET_TICK, "overflow".getBytes());

        assertEquals(1, router.droppedEventCount());
        assertEquals(SMALL_QUEUE, router.queueDepth());
    }

    @Test
    void backpressureDropsAccumulate() {
        router = new GatewayTopicRouter(SMALL_QUEUE);

        for (int i = 0; i < SMALL_QUEUE + 5; i++) {
            router.publish(GatewayTopic.MARKET_TICK, ("data-" + i).getBytes());
        }

        assertEquals(5, router.droppedEventCount());
    }

    @Test
    void backpressureDoesNotDropWhenQueueHasSpace() {
        router = new GatewayTopicRouter(SMALL_QUEUE);

        for (int i = 0; i < SMALL_QUEUE; i++) {
            router.publish(GatewayTopic.MARKET_TICK, ("data-" + i).getBytes());
        }

        assertEquals(0, router.droppedEventCount());
        assertEquals(SMALL_QUEUE, router.queueDepth());
    }

    // ── Shutdown drain ──────────────────────────────────────────────────

    @Test
    void stopDrainsRemainingEvents() throws Exception {
        router = new GatewayTopicRouter(SMALL_QUEUE);
        WebSocketSession session = openSession("s1");

        router.subscribe(session, GatewayTopic.MARKET_TICK);
        router.start();

        router.publish(GatewayTopic.MARKET_TICK, "data-1".getBytes());
        router.publish(GatewayTopic.MARKET_TICK, "data-2".getBytes());

        assertTrue(awaitSentCount(router, 2, 3000), "Timeout waiting for initial sends");

        router.publish(GatewayTopic.MARKET_TICK, "data-3".getBytes());
        router.publish(GatewayTopic.MARKET_TICK, "data-4".getBytes());

        router.stop();

        assertTrue(router.sentEventCount() >= 3,
                "Expected at least 3 events sent, got " + router.sentEventCount());
        assertEquals(0, router.queueDepth(),
                "Queue should be empty after stop");
    }

    // ── Error handling ──────────────────────────────────────────────────

    @Test
    void closedSessionIsSkippedDuringDispatch() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);

        WebSocketSession openSession = openSession("open");
        WebSocketSession closedSession = mock(WebSocketSession.class);
        when(closedSession.getId()).thenReturn("closed");
        when(closedSession.isOpen()).thenReturn(false);

        router.subscribe(openSession, GatewayTopic.MARKET_TICK);
        router.subscribe(closedSession, GatewayTopic.MARKET_TICK);

        router.start();
        router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());

        assertTrue(awaitSentCount(router, 1, 3000), "Timeout waiting for sent events");
        router.stop();

        verify(openSession).sendMessage(any(BinaryMessage.class));
        verify(closedSession, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void publishHandlesIOExceptionGracefully() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        WebSocketSession session = openSession("s1");
        doThrow(new IOException("Connection reset"))
                .when(session).sendMessage(any(BinaryMessage.class));

        router.subscribe(session, GatewayTopic.MARKET_TICK);

        router.start();
        router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());

        Thread.sleep(300);
        router.stop();

        assertEquals(0, router.sentEventCount());
    }

    @Test
    void subscribeAfterPublishAcceptsLateJoiner() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);

        router.start();
        router.publish(GatewayTopic.MARKET_TICK, "early".getBytes());

        Thread.sleep(100);

        WebSocketSession session = openSession("late-joiner");
        router.subscribe(session, GatewayTopic.MARKET_TICK);

        router.publish(GatewayTopic.MARKET_TICK, "late".getBytes());

        assertTrue(awaitSentCount(router, 1, 3000), "Timeout waiting for sent events");
        router.stop();

        verify(session).sendMessage(any(BinaryMessage.class));
    }

    // ── Concurrency ─────────────────────────────────────────────────────

    @Test
    void concurrentSubscribeAndPublishSucceeds() throws Exception {
        int sessionCount = 10;
        int publishCount = 50;
        router = new GatewayTopicRouter(1024);

        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            WebSocketSession s = mock(WebSocketSession.class);
            when(s.getId()).thenReturn("s" + i);
            when(s.isOpen()).thenReturn(true);
            sessions.add(s);
        }

        ExecutorService exec = Executors.newFixedThreadPool(4);
        CountDownLatch subscribeLatch = new CountDownLatch(sessionCount);
        for (WebSocketSession s : sessions) {
            exec.submit(() -> {
                router.subscribe(s, GatewayTopic.MARKET_TICK);
                subscribeLatch.countDown();
            });
        }
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS),
                "Subscribe phase did not complete in time");
        assertEquals(sessionCount, router.subscriberCount(GatewayTopic.MARKET_TICK));

        router.start();

        CountDownLatch publishLatch = new CountDownLatch(publishCount);
        for (int i = 0; i < publishCount; i++) {
            exec.submit(() -> {
                router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());
                publishLatch.countDown();
            });
        }
        assertTrue(publishLatch.await(5, TimeUnit.SECONDS),
                "Publish phase did not complete in time");

        long expectedSends = (long) publishCount * sessionCount;
        long deadline = System.currentTimeMillis() + 5000;
        while (router.sentEventCount() < expectedSends && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        exec.shutdown();
        router.stop();

        assertEquals(0, router.droppedEventCount(),
                "No events should be dropped with sufficient queue capacity");
        assertEquals(expectedSends, router.sentEventCount(),
                "All publishes should reach all subscribed sessions");
    }

    @Test
    void concurrentSubscribeAndUnsubscribeDoesNotCauseExceptions() throws Exception {
        router = new GatewayTopicRouter(NORMAL_QUEUE);
        ExecutorService exec = Executors.newFixedThreadPool(4);
        List<WebSocketSession> sessions = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            WebSocketSession s = mock(WebSocketSession.class, withSettings().lenient());
            when(s.getId()).thenReturn("s" + i);
            when(s.isOpen()).thenReturn(true);
            sessions.add(s);
            router.subscribe(s, GatewayTopic.MARKET_TICK);
        }

        router.start();

        // 10 unsubscribe tasks + 20 publish tasks = 30 total countdowns
        CountDownLatch latch = new CountDownLatch(30);
        for (int i = 0; i < 20; i++) {
            int idx = i;
            if (i < 10) {
                exec.submit(() -> {
                    router.unsubscribeAll(sessions.get(idx));
                    latch.countDown();
                });
            }
            exec.submit(() -> {
                router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Concurrent operations did not complete in time");
        exec.shutdown();
        router.stop();

        assertTrue(router.sentEventCount() >= 0);
    }

    // ── Metrics ─────────────────────────────────────────────────────────

    @Test
    void metricsReportCorrectly() {
        router = new GatewayTopicRouter(SMALL_QUEUE);

        assertEquals(0, router.droppedEventCount());
        assertEquals(0, router.sentEventCount());
        assertEquals(0, router.queueDepth());

        for (int i = 0; i < SMALL_QUEUE; i++) {
            router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());
        }
        assertEquals(SMALL_QUEUE, router.queueDepth());

        router.publish(GatewayTopic.MARKET_TICK, "overflow".getBytes());
        assertEquals(1, router.droppedEventCount());
        assertEquals(SMALL_QUEUE, router.queueDepth());
    }

    @Test
    void droppedEventCountIncrementsOnlyOnOverflow() {
        router = new GatewayTopicRouter(SMALL_QUEUE);

        router.publish(GatewayTopic.MARKET_TICK, "d1".getBytes());
        router.publish(GatewayTopic.MARKET_TICK, "d2".getBytes());
        router.publish(GatewayTopic.MARKET_TICK, "d3".getBytes());
        router.publish(GatewayTopic.MARKET_TICK, "d4".getBytes());
        assertEquals(0, router.droppedEventCount());

        router.publish(GatewayTopic.MARKET_TICK, "d5".getBytes());
        router.publish(GatewayTopic.MARKET_TICK, "d6".getBytes());
        assertEquals(2, router.droppedEventCount());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Create a mock session with the given ID that is open. */
    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    /**
     * Poll {@link GatewayTopicRouter#sentEventCount()} until it reaches or
     * exceeds the target. Returns {@code true} if the target was reached
     * within the timeout, {@code false} otherwise.
     */
    private static boolean awaitSentCount(GatewayTopicRouter router, long target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (router.sentEventCount() < target && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return router.sentEventCount() >= target;
    }
}
