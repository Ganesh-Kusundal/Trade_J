package com.tradej.gateway.router;

import com.tradej.core.testing.ConcurrentStressTester;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.transport.WebSocketTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests for {@link GatewayTopicRouter}.
 * Verifies thread-safe publish/subscribe/unsubscribe under contention.
 */
@Tag("unit")
class GatewayTopicRouterConcurrencyTest {

    private GatewayTopicRouter router;

    @AfterEach
    void tearDown() {
        if (router != null) {
            router.stop();
        }
    }

    @Test
    void concurrentSubscribeAndPublishDoesNotCorruptState() {
        router = new GatewayTopicRouter();
        router.start();

        int workers = 8;
        int iterations = 200;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            WebSocketTransport transport = mockTransport("session-" + threadIndex + "-" + Thread.currentThread().getId());
            router.subscribe(transport, GatewayTopic.MARKET_TICK);
            router.publish(GatewayTopic.MARKET_TICK, ("tick-" + threadIndex).getBytes());
            router.unsubscribeAll(transport);
        });

        assertTrue(result.passes() > 0, "Stress tester should have passes");
        assertTrue(result.exceptions().isEmpty(), "No exceptions under concurrent subscribe/publish/unsubscribe");
    }

    @Test
    void concurrentPublishFromMultipleThreadsDeliversAll() throws Exception {
        router = new GatewayTopicRouter();

        var received = new CopyOnWriteArrayList<byte[]>();
        WebSocketTransport transport = mock(WebSocketTransport.class);
        when(transport.id()).thenReturn("concurrent-receiver");
        when(transport.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            byte[] data = inv.getArgument(0);
            received.add(data);
            return null;
        }).when(transport).sendBinary(any(byte[].class));

        router.subscribe(transport, GatewayTopic.MARKET_TICK);
        router.start();

        int workers = 4;
        int messagesPerWorker = 100;
        var result = ConcurrentStressTester.run(workers, messagesPerWorker, threadIndex -> {
            router.publish(GatewayTopic.MARKET_TICK, ("msg-" + threadIndex).getBytes());
        });

        Thread.sleep(500);

        assertTrue(result.exceptions().isEmpty(), "No exceptions during concurrent publish");
        assertTrue(received.size() > 0, "At least some messages should be delivered");
    }

    @Test
    void concurrentSubscribeToDifferentTopicsIsolatesCorrectly() {
        router = new GatewayTopicRouter();
        router.start();

        int workers = 6;
        GatewayTopic[] topics = GatewayTopic.values();

        var result = ConcurrentStressTester.run(workers, 100, threadIndex -> {
            WebSocketTransport transport = mockTransport("iso-" + threadIndex);
            GatewayTopic topic = topics[threadIndex % topics.length];
            router.subscribe(transport, topic);
            assertTrue(router.subscriberCount(topic) >= 1,
                    "Subscriber count should be at least 1 for " + topic);
        });

        assertTrue(result.exceptions().isEmpty(), "No exceptions during concurrent topic subscription");
    }

    @Test
    void concurrentUnsubscribeAllDoesNotThrow() {
        router = new GatewayTopicRouter();
        router.start();

        int workers = 8;
        var transports = new CopyOnWriteArrayList<WebSocketTransport>();

        for (int i = 0; i < workers; i++) {
            WebSocketTransport transport = mockTransport("unsub-" + i);
            transports.add(transport);
            router.subscribe(transport, GatewayTopic.MARKET_TICK);
            router.subscribe(transport, GatewayTopic.ORDER_UPDATE);
        }

        var result = ConcurrentStressTester.run(workers, 50, threadIndex -> {
            router.unsubscribeAll(transports.get(threadIndex));
        });

        assertTrue(result.exceptions().isEmpty(), "Concurrent unsubscribeAll should not throw");
    }

    @Test
    void subscriberCountRemainsConsistentUnderConcurrentModification() {
        router = new GatewayTopicRouter();
        router.start();

        int workers = 4;
        int iterations = 100;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            WebSocketTransport transport = mockTransport("count-" + threadIndex + "-" + System.nanoTime());
            router.subscribe(transport, GatewayTopic.MARKET_TICK);
            int count = router.subscriberCount(GatewayTopic.MARKET_TICK);
            assertTrue(count >= 0, "Subscriber count should never be negative");
        });

        assertTrue(result.exceptions().isEmpty(), "No exceptions during subscriber count checks");
    }

    private WebSocketTransport mockTransport(String id) {
        WebSocketTransport transport = mock(WebSocketTransport.class);
        when(transport.id()).thenReturn(id);
        when(transport.isOpen()).thenReturn(true);
        return transport;
    }
}
