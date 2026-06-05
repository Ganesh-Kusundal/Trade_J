package com.tradej.gateway.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GatewayEventBridge}.
 *
 * <p>Covers event-ID dedup, TTL-based throttled eviction, periodic pruner,
 * metrics, lifecycle, and routing integration with {@link GatewayTopicRouter}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GatewayEventBridgeTest {

    @Mock
    private GatewayTopicRouter router;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GatewayEventBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new GatewayEventBridge(router, objectMapper);
    }

    @AfterEach
    void tearDown() {
        bridge.close();
    }

    // ── Test event helper ───────────────────────────────────────────────

    /** Minimal DomainEvent implementation for dedup testing. */
    private record TestEvent(EventMetadata metadata) implements DomainEvent {
        @Override
        public void accept(DomainEventVisitor visitor) {
            // No-op for test event
        }
    }

    private static TestEvent eventWithId(String eventId) {
        return new TestEvent(new EventMetadata(eventId, 0L, 0L, 0L, "", 1));
    }

    // ── Basic dedup ─────────────────────────────────────────────────────

    @Test
    void sameEventIdIsDeduplicated() {
        DomainEvent event = eventWithId("evt-001");

        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);

        assertEquals(1, bridge.eventCount(),
                "Same event should only be processed once");
        assertEquals(1, bridge.dedupHitCount(),
                "Dedup should count one hit");
    }

    @Test
    void differentEventIdsBothPassThrough() {
        bridge.onDomainEvent(eventWithId("evt-001"));
        bridge.onDomainEvent(eventWithId("evt-002"));

        assertEquals(2, bridge.eventCount(),
                "Different events should both be processed");
        assertEquals(0, bridge.dedupHitCount(),
                "No dedup hits for unique events");
    }

    @Test
    void duplicateAfterDifferentEventIsDeduplicated() {
        bridge.onDomainEvent(eventWithId("evt-001"));
        bridge.onDomainEvent(eventWithId("evt-002"));
        bridge.onDomainEvent(eventWithId("evt-001"));

        assertEquals(2, bridge.eventCount(),
                "Only unique events should increment count");
        assertEquals(1, bridge.dedupHitCount(),
                "One dedup hit for the repeated event");
    }

    @Test
    void multipleDuplicatesAreAllSkipped() {
        DomainEvent event = eventWithId("dup-id");

        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);

        assertEquals(1, bridge.eventCount(),
                "Multiple duplicates should still count as one");
        assertEquals(3, bridge.dedupHitCount(),
                "All three subsequent calls should be dedup hits");
    }

    // ── Metrics ─────────────────────────────────────────────────────────

    @Test
    void metricsAreZeroInitially() {
        assertEquals(0, bridge.eventCount());
        assertEquals(0, bridge.dedupHitCount());
        assertEquals(0, bridge.dedupCacheSize());
    }

    @Test
    void dedupCacheSizeTracksSeenEvents() {
        assertEquals(0, bridge.dedupCacheSize());

        bridge.onDomainEvent(eventWithId("a"));
        assertEquals(1, bridge.dedupCacheSize(),
                "Cache size should increase after first event");

        bridge.onDomainEvent(eventWithId("b"));
        assertEquals(2, bridge.dedupCacheSize(),
                "Cache size should increase for different event");

        bridge.onDomainEvent(eventWithId("a"));
        assertEquals(2, bridge.dedupCacheSize(),
                "Cache size should not increase for duplicate");
    }

    @Test
    void dedupHitCountIncrementsOnlyOnDuplicates() {
        bridge.onDomainEvent(eventWithId("x"));
        assertEquals(0, bridge.dedupHitCount());

        bridge.onDomainEvent(eventWithId("x"));
        assertEquals(1, bridge.dedupHitCount());

        bridge.onDomainEvent(eventWithId("x"));
        assertEquals(2, bridge.dedupHitCount());

        bridge.onDomainEvent(eventWithId("y"));
        assertEquals(2, bridge.dedupHitCount());
    }

    @Test
    void eventCountIncrementsOnlyForNonDuplicateEvents() {
        bridge.onDomainEvent(eventWithId("a"));
        assertEquals(1, bridge.eventCount());

        bridge.onDomainEvent(eventWithId("a"));
        assertEquals(1, bridge.eventCount(),
                "Duplicate should not increment eventCount");

        bridge.onDomainEvent(eventWithId("b"));
        assertEquals(2, bridge.eventCount());
    }

    // ── TTL eviction (via reflection) ───────────────────────────────────

    @Test
    void throttledEvictionRemovesStaleEntries() throws Exception {
        // Access the internal seenEventIds map via reflection
        Field seenField = GatewayEventBridge.class.getDeclaredField("seenEventIds");
        seenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> map =
                (ConcurrentHashMap<String, Long>) seenField.get(bridge);

        Field counterField = GatewayEventBridge.class.getDeclaredField("bridgeCallCounter");
        counterField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong callCounter =
                (java.util.concurrent.atomic.AtomicLong) counterField.get(bridge);

        // Fill the map with entries just below the TTL eviction threshold
        // and set their timestamps to be older than the 30s TTL.
        long oldTimestamp = System.currentTimeMillis() - 35_000;
        for (int i = 0; i < 200_000; i++) {
            map.put("stale-" + i, oldTimestamp);
        }
        assertEquals(200_000, map.size(), "Map should be full");

        // Advance the call counter to just before the next eviction trigger.
        // The eviction condition fires when (counter & (1024 - 1)) == 0,
        // i.e. when counter is a multiple of 1024.
        callCounter.set(1023);
        assertEquals(1023, callCounter.get());

        // Sending a new event should trigger eviction (counter becomes 1024,
        // which is a multiple of 1024, and map size >= 200_000).
        bridge.onDomainEvent(eventWithId("fresh-event"));

        // After eviction, stale entries (older than 30s) should be removed.
        assertTrue(map.size() < 200_000,
                "Stale entries should have been evicted. Size: " + map.size());
        assertTrue(map.containsKey("fresh-event"),
                "The new event should remain in the cache");
    }

    @Test
    void evictionDoesNotRemoveRecentEntries() throws Exception {
        Field seenField = GatewayEventBridge.class.getDeclaredField("seenEventIds");
        seenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> map =
                (ConcurrentHashMap<String, Long>) seenField.get(bridge);

        Field counterField = GatewayEventBridge.class.getDeclaredField("bridgeCallCounter");
        counterField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong callCounter =
                (java.util.concurrent.atomic.AtomicLong) counterField.get(bridge);

        // Add entries with recent timestamps
        long recentTimestamp = System.currentTimeMillis();
        for (int i = 0; i < 200_000; i++) {
            map.put("recent-" + i, recentTimestamp);
        }

        // Advance counter to trigger eviction
        callCounter.set(1023);
        bridge.onDomainEvent(eventWithId("another-fresh-event"));

        // Recent entries should not be evicted (they're within the 30s TTL)
        assertTrue(map.containsKey("recent-0"),
                "Recent entries should NOT be evicted");
        assertTrue(map.containsKey("another-fresh-event"),
                "The new event should also be in the cache");
        assertTrue(map.size() > 199_000,
                "Most recent entries should be retained. Size: " + map.size());
    }

    @Test
    void evictionOnlyRunsThrottled() throws Exception {
        Field seenField = GatewayEventBridge.class.getDeclaredField("seenEventIds");
        seenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> map =
                (ConcurrentHashMap<String, Long>) seenField.get(bridge);

        Field counterField = GatewayEventBridge.class.getDeclaredField("bridgeCallCounter");
        counterField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong callCounter =
                (java.util.concurrent.atomic.AtomicLong) counterField.get(bridge);

        // Fill map with stale entries
        long oldTimestamp = System.currentTimeMillis() - 35_000;
        for (int i = 0; i < 200_000; i++) {
            map.put("stale-" + i, oldTimestamp);
        }

        // Set counter to a non-multiple of 1024
        callCounter.set(100);
        bridge.onDomainEvent(eventWithId("evt-a"));

        // Eviction should NOT have run (counter 101 is not a multiple of 1024)
        assertEquals(200_001, map.size(),
                "Eviction should not run when counter is not a multiple of 1024");
    }

    // ── Periodic pruner (via reflection) ────────────────────────────────

    @Test
    void pruneDedupCacheRemovesStaleEntries() throws Exception {
        Field seenField = GatewayEventBridge.class.getDeclaredField("seenEventIds");
        seenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> map =
                (ConcurrentHashMap<String, Long>) seenField.get(bridge);

        // Add a mix of stale and recent entries
        map.put("stale-1", System.currentTimeMillis() - 35_000);
        map.put("stale-2", System.currentTimeMillis() - 40_000);
        map.put("fresh-1", System.currentTimeMillis());
        map.put("fresh-2", System.currentTimeMillis() + 5_000);

        // Invoke private pruneDedupCache() via reflection
        Method pruneMethod = GatewayEventBridge.class.getDeclaredMethod("pruneDedupCache");
        pruneMethod.setAccessible(true);
        pruneMethod.invoke(bridge);

        // Stale entries should be removed
        assertFalse(map.containsKey("stale-1"), "Stale entry should be pruned");
        assertFalse(map.containsKey("stale-2"), "Stale entry should be pruned");
        assertTrue(map.containsKey("fresh-1"), "Recent entry should be retained");
        assertTrue(map.containsKey("fresh-2"), "Recent entry should be retained");
        assertEquals(2, map.size(), "Only fresh entries should remain");
    }

    @Test
    void pruneDedupCacheWithNoStaleEntriesDoesNothing() throws Exception {
        Field seenField = GatewayEventBridge.class.getDeclaredField("seenEventIds");
        seenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> map =
                (ConcurrentHashMap<String, Long>) seenField.get(bridge);

        map.put("fresh-1", System.currentTimeMillis());
        map.put("fresh-2", System.currentTimeMillis());

        Method pruneMethod = GatewayEventBridge.class.getDeclaredMethod("pruneDedupCache");
        pruneMethod.setAccessible(true);
        pruneMethod.invoke(bridge);

        assertEquals(2, map.size(), "All entries are fresh, none should be pruned");
    }

    @Test
    void pruneDedupCacheWithEmptyMapDoesNothing() throws Exception {
        Field seenField = GatewayEventBridge.class.getDeclaredField("seenEventIds");
        seenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> map =
                (ConcurrentHashMap<String, Long>) seenField.get(bridge);

        assertEquals(0, map.size());

        Method pruneMethod = GatewayEventBridge.class.getDeclaredMethod("pruneDedupCache");
        pruneMethod.setAccessible(true);
        pruneMethod.invoke(bridge);

        assertEquals(0, map.size(), "Empty map should remain empty after prune");
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Test
    void closeShutsDownPruner() throws Exception {
        Field prunerField = GatewayEventBridge.class.getDeclaredField("dedupPruner");
        prunerField.setAccessible(true);
        var pruner = (java.util.concurrent.ScheduledExecutorService) prunerField.get(bridge);

        assertFalse(pruner.isShutdown(), "Pruner should be running before close");

        bridge.close();

        assertTrue(pruner.isShutdown(), "Pruner should be shut down after close");
    }

    @Test
    void closeIsIdempotent() {
        bridge.close();
        bridge.close();
    }

    // ── Routing integration ─────────────────────────────────────────────

    @Test
    void pnlUpdatedEventRoutedToPnlTopic() throws Exception {
        var pnlEvent = new PnlUpdatedEvent(
                EventMetadata.root(), 1000L, 500L, 2000L);

        bridge.onDomainEvent(pnlEvent);

        // Allow async publisher to process
        Thread.sleep(150);

        ArgumentCaptor<GatewayTopic> topicCaptor = ArgumentCaptor.forClass(GatewayTopic.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(router, atLeastOnce()).publish(topicCaptor.capture(), payloadCaptor.capture());

        assertEquals(GatewayTopic.PNL_UPDATE, topicCaptor.getValue(),
                "PnlUpdatedEvent should be routed to PNL_UPDATE topic");
        assertNotNull(payloadCaptor.getValue(), "Payload should not be null");
        assertTrue(payloadCaptor.getValue().length > 0, "Payload should not be empty");
    }

    @Test
    void unknownEventTypeIsSilentlyIgnored() {
        // TestEvent is not handled in the switch (falls through to default)
        bridge.onDomainEvent(eventWithId("unknown-type"));

        verify(router, never()).publish(any(), any(byte[].class));
    }

    @Test
    void eventCountStillIncrementedForUnhandledTypes() {
        bridge.onDomainEvent(eventWithId("unknown-1"));
        bridge.onDomainEvent(eventWithId("unknown-2"));

        assertEquals(2, bridge.eventCount(),
                "eventCount should increment even for unhandled event types");
    }

    // ── Null safety ─────────────────────────────────────────────────────

    @Test
    void nullEventDoesNotPropagate() {
        // onDomainEvent calls Objects.requireNonNull which throws NPE for null.
        // This should be caught by the catch block.
        bridge.onDomainEvent(null);

        assertEquals(0, bridge.eventCount(),
                "Null event should not be processed");
        verify(router, never()).publish(any(), any(byte[].class));
    }
}
