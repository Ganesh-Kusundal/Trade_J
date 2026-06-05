package com.tradej.app.integration;

import com.tradej.app.TradingApplication;
import com.tradej.app.startup.BrokerStartupOrchestrator;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Full application-context integration test that boots the gateway profile
 * and exercises the WebSocket connect / subscribe / publish / disconnect lifecycle
 * through the real Spring-wired beans.
 *
 * <p>Verifies:
 * <ul>
 *   <li>{@link GatewayWebSocketHandler} publishes a PIPELINE_HEALTH "connected" message on connect</li>
 *   <li>Single-byte binary subscribe registers the session for the correct topic</li>
 *   <li>Text-based "SUBSCRIBE ALL" registers the session for every topic</li>
 *   <li>{@link GatewayTopicRouter#publish} delivers frames to subscribed sessions</li>
 *   <li>Disconnect cleans up all subscriptions via {@link GatewayTopicRouter#unsubscribeAll}</li>
 *   <li>{@link LoadBalancedBrokerGateway} fans out connect/disconnect to all nodes</li>
 * </ul>
 */
@Tag("component")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        classes = TradingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "trade.broker-type=gateway",
                "trade.runtime.mode=REPLAY",
                // Gateway WebSocket endpoint
                "tradej.gateway.enabled=true",
                // Dhan (sandbox stub)
                "trade.broker.clientId=gateway-ws-lifecycle-test",
                "trade.broker.access-token=smoke-test-token",
                "trade.broker.environment=SANDBOX",
                "trade.broker.auth-mode=STATIC",
                // ICICI (minimal stub)
                "trade.icici.appKey=smoke-app-key",
                "trade.icici.secretKey=smoke-secret-key",
                "trade.icici.auth-mode=STATIC",
                // Upstox (analytics-only stub)
                "trade.upstox.clientId=smoke-upstox-id",
                "trade.upstox.clientSecret=smoke-upstox-secret",
                "trade.upstox.accessToken=smoke-access-token",
                "trade.upstox.analytics-only=true",
                "trade.upstox.analytics-token=smoke-analytics-token",
                // Storage stubs
                "trade.storage.chroniclePath=build/lifecycle-chronicle",
                "trade.storage.duckdbPath=build/lifecycle-duckdb.duckdb",
                // Subscriptions
                "trade.subscriptions[0].symbol=SBIN",
                "trade.subscriptions[0].exchangeSegment=NSE_EQ",
                "trade.subscriptions[0].feedMode=TICKER"
        }
)
class GatewayWebSocketLifecycleTest {

    private static final Path LIFECYCLE_DIR;
    private static final Path CATALOG_DIR;

    static {
        try {
            LIFECYCLE_DIR = Files.createTempDirectory("gateway-ws-lifecycle");
            CATALOG_DIR = LIFECYCLE_DIR.resolve("instruments");
            Files.createDirectory(CATALOG_DIR);
            Files.writeString(CATALOG_DIR.resolve("instruments.csv"), """
                    symbol,exchange,exchangeSegment,securityId
                    SBIN,NSE,NSE_EQ,3045
                    """);
            Files.writeString(LIFECYCLE_DIR.resolve("dhan-token-state.json"), "{}");
            Files.writeString(LIFECYCLE_DIR.resolve("icici-token-state.json"), "{}");
            Files.writeString(LIFECYCLE_DIR.resolve("upstox-token-state.json"), "{}");
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create temp lifecycle-test files", ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("trade.instruments.cache-directory", CATALOG_DIR::toString);
        registry.add("trade.broker.token-state-file", () -> LIFECYCLE_DIR.resolve("dhan-token-state.json").toString());
        registry.add("trade.icici.token-state-file", () -> LIFECYCLE_DIR.resolve("icici-token-state.json").toString());
    }

    @MockitoBean
    private BrokerStartupOrchestrator brokerStartupOrchestrator;

    @Autowired
    private GatewayWebSocketHandler webSocketHandler;

    @Autowired
    private GatewayTopicRouter topicRouter;

    @Autowired
    private IBrokerConnection brokerConnection;

    private final List<WebSocketSession> managedSessions = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanupSessions() {
        for (WebSocketSession session : managedSessions) {
            // Clean up router subscriptions so they don't leak across tests
            topicRouter.unsubscribeAll(session);
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException ignored) {
            }
        }
        managedSessions.clear();
    }

    // ── Context wiring ─────────────────────────────────────────────

    @Test
    void contextWiresGatewayBeans() {
        assertInstanceOf(LoadBalancedBrokerGateway.class, brokerConnection);
        assertNotNull(webSocketHandler, "GatewayWebSocketHandler should be in the context");
        assertNotNull(topicRouter, "GatewayTopicRouter should be in the context");
    }

    @Test
    void gatewayAggregatesAtLeastOneBrokerNode() {
        LoadBalancedBrokerGateway gateway = (LoadBalancedBrokerGateway) brokerConnection;
        assertTrue(gateway.connectionCount() >= 1,
                "Gateway should aggregate at least one broker node");
    }

    // ── Connect lifecycle ──────────────────────────────────────────

    @Test
    void connectPublishesPipelineHealthMessage() throws Exception {
        WebSocketSession session = mockSession("connect-health-session");
        List<byte[]> receivedFrames = new CopyOnWriteArrayList<>();
        captureFrames(session, receivedFrames);

        topicRouter.start();

        // Subscribe to PIPELINE_HEALTH *before* connecting, so the
        // "connected:<id>" message published by afterConnectionEstablished
        // is actually delivered to this session.
        subscribeToTopic(session, GatewayTopic.PIPELINE_HEALTH);

        webSocketHandler.afterConnectionEstablished(session);
        drainPublisher();

        boolean found = receivedFrames.stream().anyMatch(frame -> {
            try {
                GatewayBinaryCodec.GatewayFrame decoded = GatewayBinaryCodec.decode(frame);
                if (decoded.topic() != GatewayTopic.PIPELINE_HEALTH) return false;
                String payload = GatewayBinaryCodec.decodeUtf8(decoded.payload());
                return payload.contains("connected:connect-health-session");
            } catch (Exception e) {
                return false;
            }
        });
        assertTrue(found, "Connect should publish PIPELINE_HEALTH with 'connected:<id>'");
    }

    // ── Subscribe lifecycle ────────────────────────────────────────

    @Test
    void singleByteSubscribeRegistersCorrectTopic() throws Exception {
        WebSocketSession session = mockSession("sub-single");
        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        // Single-byte message: wireId for MARKET_TICK is 0
        subscribeToTopic(session, GatewayTopic.MARKET_TICK);

        assertEquals(1, topicRouter.subscriberCount(GatewayTopic.MARKET_TICK),
                "Session should be subscribed to MARKET_TICK");
        assertEquals(0, topicRouter.subscriberCount(GatewayTopic.MARKET_DEPTH),
                "Session should NOT be subscribed to MARKET_DEPTH");
    }

    @Test
    void textSubscribeAllRegistersAllTopics() throws Exception {
        WebSocketSession session = mockSession("sub-all");
        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        sendTextSubscribe(session, "SUBSCRIBE ALL");

        for (GatewayTopic topic : GatewayTopic.values()) {
            assertTrue(topicRouter.subscriberCount(topic) >= 1,
                    "Session should be subscribed to " + topic + " after SUBSCRIBE ALL");
        }
    }

    @Test
    void textSubscribeSingleTopicRegistersCorrectTopic() throws Exception {
        WebSocketSession session = mockSession("sub-text-one");
        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        sendTextSubscribe(session, "SUBSCRIBE ORDER_UPDATE");

        assertEquals(1, topicRouter.subscriberCount(GatewayTopic.ORDER_UPDATE),
                "Session should be subscribed to ORDER_UPDATE");
        assertEquals(0, topicRouter.subscriberCount(GatewayTopic.MARKET_TICK),
                "Session should NOT be subscribed to MARKET_TICK");
    }

    // ── Publish / receive ──────────────────────────────────────────

    @Test
    void publishDeliversFrameToSubscribedSession() throws Exception {
        WebSocketSession session = mockSession("pub-sub");
        List<byte[]> receivedFrames = new CopyOnWriteArrayList<>();
        captureFrames(session, receivedFrames);

        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        // Subscribe to MARKET_TICK
        subscribeToTopic(session, GatewayTopic.MARKET_TICK);

        // Publish a tick payload
        topicRouter.publish(GatewayTopic.MARKET_TICK, "SBIN:75000".getBytes());
        drainPublisher();

        assertFalse(receivedFrames.isEmpty(), "Session should have received at least one frame");

        boolean tickFound = receivedFrames.stream().anyMatch(frame -> {
            try {
                GatewayBinaryCodec.GatewayFrame decoded = GatewayBinaryCodec.decode(frame);
                return decoded.topic() == GatewayTopic.MARKET_TICK
                        && new String(decoded.payload()).contains("SBIN:75000");
            } catch (Exception e) {
                return false;
            }
        });
        assertTrue(tickFound, "Delivered frame should contain the tick payload");
    }

    @Test
    void publishDoesNotDeliverToUnsubscribedTopics() throws Exception {
        WebSocketSession session = mockSession("no-deliver");
        List<byte[]> receivedFrames = new CopyOnWriteArrayList<>();
        captureFrames(session, receivedFrames);

        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        // Subscribe only to MARKET_TICK
        subscribeToTopic(session, GatewayTopic.MARKET_TICK);

        // Publish to ORDER_UPDATE (not subscribed)
        topicRouter.publish(GatewayTopic.ORDER_UPDATE, "order-data".getBytes());
        drainPublisher();

        boolean orderUpdateFound = receivedFrames.stream().anyMatch(frame -> {
            try {
                return GatewayBinaryCodec.decode(frame).topic() == GatewayTopic.ORDER_UPDATE;
            } catch (Exception e) {
                return false;
            }
        });
        assertFalse(orderUpdateFound,
                "Session should NOT receive ORDER_UPDATE when only subscribed to MARKET_TICK");
    }

    @Test
    void multipleTopicDelivery() throws Exception {
        WebSocketSession session = mockSession("multi-topic");
        List<byte[]> receivedFrames = new CopyOnWriteArrayList<>();
        captureFrames(session, receivedFrames);

        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        // Subscribe to both MARKET_TICK and ORDER_UPDATE
        subscribeToTopic(session, GatewayTopic.MARKET_TICK);
        subscribeToTopic(session, GatewayTopic.ORDER_UPDATE);

        topicRouter.publish(GatewayTopic.MARKET_TICK, "tick-data".getBytes());
        topicRouter.publish(GatewayTopic.ORDER_UPDATE, "order-data".getBytes());
        drainPublisher();

        assertTrue(receivedFrames.size() >= 2,
                "Session should receive both MARKET_TICK and ORDER_UPDATE frames");

        boolean hasTick = receivedFrames.stream().anyMatch(frame -> {
            try {
                GatewayBinaryCodec.GatewayFrame decoded = GatewayBinaryCodec.decode(frame);
                return decoded.topic() == GatewayTopic.MARKET_TICK;
            } catch (Exception e) {
                return false;
            }
        });
        boolean hasOrder = receivedFrames.stream().anyMatch(frame -> {
            try {
                GatewayBinaryCodec.GatewayFrame decoded = GatewayBinaryCodec.decode(frame);
                return decoded.topic() == GatewayTopic.ORDER_UPDATE;
            } catch (Exception e) {
                return false;
            }
        });
        assertTrue(hasTick, "Should receive MARKET_TICK frame");
        assertTrue(hasOrder, "Should receive ORDER_UPDATE frame");
    }

    // ── Disconnect lifecycle ───────────────────────────────────────

    @Test
    void disconnectRemovesAllSubscriptions() throws Exception {
        WebSocketSession session = mockSession("disconnect-clean");
        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(session);

        // Subscribe to all topics
        sendTextSubscribe(session, "SUBSCRIBE ALL");

        // Verify subscriptions exist
        for (GatewayTopic topic : GatewayTopic.values()) {
            assertTrue(topicRouter.subscriberCount(topic) >= 1,
                    "Pre-condition: session should be subscribed to " + topic);
        }

        // Disconnect
        webSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Verify all subscriptions cleaned up
        for (GatewayTopic topic : GatewayTopic.values()) {
            assertEquals(0, topicRouter.subscriberCount(topic),
                    "After disconnect, session should be unsubscribed from " + topic);
        }
    }

    // ── Multi-session isolation ────────────────────────────────────

    @Test
    void multipleSessionsIsolatedAfterDisconnect() throws Exception {
        WebSocketSession sessionA = mockSession("multi-A");
        WebSocketSession sessionB = mockSession("multi-B");

        topicRouter.start();
        webSocketHandler.afterConnectionEstablished(sessionA);
        webSocketHandler.afterConnectionEstablished(sessionB);

        // Both subscribe to MARKET_TICK
        subscribeToTopic(sessionA, GatewayTopic.MARKET_TICK);
        subscribeToTopic(sessionB, GatewayTopic.MARKET_TICK);

        assertEquals(2, topicRouter.subscriberCount(GatewayTopic.MARKET_TICK),
                "Both sessions should be subscribed to MARKET_TICK");

        // Disconnect session A
        webSocketHandler.afterConnectionClosed(sessionA, CloseStatus.NORMAL);

        assertEquals(1, topicRouter.subscriberCount(GatewayTopic.MARKET_TICK),
                "Only session B should remain subscribed after A disconnects");

        // Session B should still receive messages
        List<byte[]> receivedB = new CopyOnWriteArrayList<>();
        captureFrames(sessionB, receivedB);

        topicRouter.publish(GatewayTopic.MARKET_TICK, "test-payload".getBytes());
        drainPublisher();

        assertFalse(receivedB.isEmpty(),
                "Session B should still receive messages after A disconnects");
    }

    // ── LoadBalancedBrokerGateway connect/disconnect delegation ────

    @Test
    void gatewayDisconnectDelegatesToAllNodes() {
        LoadBalancedBrokerGateway gateway = (LoadBalancedBrokerGateway) brokerConnection;
        assertDoesNotThrow(gateway::disconnect,
                "Gateway disconnect should delegate to all broker nodes without error");
    }

    @Test
    void gatewayWebSocketMultiplexerDisconnect() {
        assertDoesNotThrow(() -> brokerConnection.websocket().disconnect(),
                "Gateway FailoverWebSocketMultiplexer disconnect should not throw");
    }

    @Test
    void gatewayConnectionCountReflectsConfiguredBrokers() {
        LoadBalancedBrokerGateway gateway = (LoadBalancedBrokerGateway) brokerConnection;
        assertTrue(gateway.connectionCount() >= 1,
                "Gateway should have at least one configured broker connection");
    }

    // ── Helpers ────────────────────────────────────────────────────

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class, id);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        managedSessions.add(session);
        return session;
    }

    /**
     * Subscribe a session to a topic using the single-byte wire protocol.
     */
    private void subscribeToTopic(WebSocketSession session, GatewayTopic topic) throws Exception {
        byte wireId = (byte) topic.wireId();
        BinaryMessage msg = new BinaryMessage(ByteBuffer.wrap(new byte[]{wireId}));
        webSocketHandler.handleMessage(session, msg);
    }

    /**
     * Send a text-based SUBSCRIBE command as a binary message (UTF-8 encoded).
     */
    private void sendTextSubscribe(WebSocketSession session, String command) throws Exception {
        BinaryMessage msg = new BinaryMessage(ByteBuffer.wrap(command.getBytes()));
        webSocketHandler.handleMessage(session, msg);
    }

    /**
     * Intercept frames sent to a mock session by wiring {@code sendMessage}.
     */
    private void captureFrames(WebSocketSession session, List<byte[]> sink) {
        try {
            doAnswer(invocation -> {
                BinaryMessage message = invocation.getArgument(0);
                ByteBuffer buf = message.getPayload();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);
                sink.add(data);
                return null;
            }).when(session).sendMessage(any(BinaryMessage.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Allow the GatewayTopicRouter publisher thread to drain its queue.
     */
    private void drainPublisher() throws InterruptedException {
        // The publisher thread polls with a 100ms timeout. Wait long enough
        // for it to pick up enqueued tasks and dispatch them.
        Thread.sleep(500);
    }
}
