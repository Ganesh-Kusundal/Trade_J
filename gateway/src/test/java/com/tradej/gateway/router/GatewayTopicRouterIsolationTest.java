package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.transport.WebSocketTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for per-transport isolation in {@link GatewayTopicRouter}.
 * Verifies that slow transports don't block others and that per-transport
 * drop counters work correctly.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GatewayTopicRouterIsolationTest {

    private GatewayTopicRouter router;

    @AfterEach
    void tearDown() {
        if (router != null) {
            router.stop();
        }
    }

    /**
     * Test: slow transport doesn't block other transports.
     * One transport blocks on sendBinary, while another should still receive messages.
     */
    @Test
    void slowTransportDoesNotBlockOtherTransports() throws Exception {
        router = new GatewayTopicRouter(1024, 1024);

        // Slow transport that blocks on sendBinary
        WebSocketTransport slowTransport = mock(WebSocketTransport.class);
        when(slowTransport.id()).thenReturn("slow");
        when(slowTransport.isOpen()).thenReturn(true);
        CountDownLatch slowLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            slowLatch.await(5, TimeUnit.SECONDS);
            return null;
        }).when(slowTransport).sendBinary(any(byte[].class));

        // Fast transport
        WebSocketTransport fastTransport = mock(WebSocketTransport.class);
        when(fastTransport.id()).thenReturn("fast");
        when(fastTransport.isOpen()).thenReturn(true);
        CountDownLatch fastLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            fastLatch.countDown();
            return null;
        }).when(fastTransport).sendBinary(any(byte[].class));

        router.subscribe(slowTransport, GatewayTopic.MARKET_TICK);
        router.subscribe(fastTransport, GatewayTopic.MARKET_TICK);
        router.start();

        // Publish a message - both transports should receive it
        router.publish(GatewayTopic.MARKET_TICK, "test".getBytes());

        // Fast transport should receive the message even though slow transport is blocked
        assertTrue(fastLatch.await(3, TimeUnit.SECONDS),
                "Fast transport should receive message even when slow transport is blocked");

        // Verify fast transport received the message
        verify(fastTransport, atLeastOnce()).sendBinary(any(byte[].class));

        // Release the slow transport
        slowLatch.countDown();
    }

    /**
     * Test: queue-full increments drop counter.
     * When a transport's per-transport queue is full, the drop counter should increment.
     */
    @Test
    void queueFullIncrementsDropCounter() throws Exception {
        // Small per-transport queue capacity
        router = new GatewayTopicRouter(1024, 2);

        WebSocketTransport transport = mock(WebSocketTransport.class);
        when(transport.id()).thenReturn("test-transport");
        when(transport.isOpen()).thenReturn(true);

        // Block the transport so its queue fills up
        CountDownLatch blockLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            blockLatch.await(5, TimeUnit.SECONDS);
            return null;
        }).when(transport).sendBinary(any(byte[].class));

        router.subscribe(transport, GatewayTopic.MARKET_TICK);
        router.start();

        // Publish many messages to fill the per-transport queue
        for (int i = 0; i < 10; i++) {
            router.publish(GatewayTopic.MARKET_TICK, ("msg-" + i).getBytes());
        }

        // Wait for messages to be dispatched to the per-transport queue
        Thread.sleep(500);

        // Check that drops occurred (queue capacity is 2, we sent 10)
        long drops = router.dropCount(transport);
        assertTrue(drops > 0, "Drop counter should be > 0 when queue is full, got: " + drops);

        // Release the transport
        blockLatch.countDown();
    }

    /**
     * Test: publish to multiple transports delivers to all.
     * All subscribed transports should receive the published message.
     */
    @Test
    void publishToMultipleTransportsDeliversToAll() throws Exception {
        router = new GatewayTopicRouter(1024, 1024);

        int transportCount = 5;
        WebSocketTransport[] transports = new WebSocketTransport[transportCount];
        CountDownLatch[] latches = new CountDownLatch[transportCount];

        for (int i = 0; i < transportCount; i++) {
            transports[i] = mock(WebSocketTransport.class);
            when(transports[i].id()).thenReturn("transport-" + i);
            when(transports[i].isOpen()).thenReturn(true);

            latches[i] = new CountDownLatch(1);
            final CountDownLatch latch = latches[i];
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(transports[i]).sendBinary(any(byte[].class));

            router.subscribe(transports[i], GatewayTopic.MARKET_TICK);
        }

        router.start();
        router.publish(GatewayTopic.MARKET_TICK, "broadcast".getBytes());

        // All transports should receive the message
        for (int i = 0; i < transportCount; i++) {
            assertTrue(latches[i].await(3, TimeUnit.SECONDS),
                    "Transport " + i + " should receive the message");
            verify(transports[i], atLeastOnce()).sendBinary(any(byte[].class));
        }
    }

    /**
     * Test: dropCount returns zero for unknown transport.
     */
    @Test
    void dropCountReturnsZeroForUnknownTransport() {
        router = new GatewayTopicRouter();

        WebSocketTransport unknown = mock(WebSocketTransport.class);
        when(unknown.id()).thenReturn("unknown");

        assertEquals(0, router.dropCount(unknown),
                "dropCount should return 0 for unknown transport");
    }

    /**
     * Test: dropCount returns zero when queue is not full.
     */
    @Test
    void dropCountReturnsZeroWhenQueueNotFull() throws Exception {
        router = new GatewayTopicRouter(1024, 1024);

        WebSocketTransport transport = mock(WebSocketTransport.class);
        when(transport.id()).thenReturn("test");
        when(transport.isOpen()).thenReturn(true);

        router.subscribe(transport, GatewayTopic.MARKET_TICK);
        router.start();

        // Publish a few messages (well within queue capacity)
        for (int i = 0; i < 5; i++) {
            router.publish(GatewayTopic.MARKET_TICK, ("msg-" + i).getBytes());
        }

        Thread.sleep(300);

        assertEquals(0, router.dropCount(transport),
                "dropCount should be 0 when queue is not full");
    }

    /**
     * Test: per-transport isolation with concurrent publishes.
     * Multiple threads publishing should not cause cross-transport blocking.
     */
    @Test
    void concurrentPublishWithIsolation() throws Exception {
        router = new GatewayTopicRouter(1024, 1024);

        int transportCount = 3;
        WebSocketTransport[] transports = new WebSocketTransport[transportCount];
        AtomicInteger[] sendCounts = new AtomicInteger[transportCount];

        for (int i = 0; i < transportCount; i++) {
            transports[i] = mock(WebSocketTransport.class);
            when(transports[i].id()).thenReturn("t" + i);
            when(transports[i].isOpen()).thenReturn(true);
            sendCounts[i] = new AtomicInteger(0);

            final AtomicInteger counter = sendCounts[i];
            doAnswer(inv -> {
                counter.incrementAndGet();
                return null;
            }).when(transports[i]).sendBinary(any(byte[].class));

            router.subscribe(transports[i], GatewayTopic.MARKET_TICK);
        }

        router.start();

        // Publish from multiple threads
        int publishCount = 20;
        Thread[] publishers = new Thread[4];
        for (int t = 0; t < publishers.length; t++) {
            publishers[t] = new Thread(() -> {
                for (int i = 0; i < publishCount; i++) {
                    router.publish(GatewayTopic.MARKET_TICK, "data".getBytes());
                }
            });
            publishers[t].start();
        }

        for (Thread publisher : publishers) {
            publisher.join(5000);
        }

        // Wait for all messages to be delivered
        Thread.sleep(1000);

        // Each transport should receive all published messages
        int totalPublished = publishCount * publishers.length;
        for (int i = 0; i < transportCount; i++) {
            assertEquals(totalPublished, sendCounts[i].get(),
                    "Transport " + i + " should receive all " + totalPublished + " messages");
        }

        // No drops should occur with sufficient queue capacity
        for (int i = 0; i < transportCount; i++) {
            assertEquals(0, router.dropCount(transports[i]),
                    "No drops should occur with sufficient queue capacity");
        }
    }
}
