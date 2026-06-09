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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GatewayEventBridge}.
 *
 * <p>Covers event routing, metrics, lifecycle, and null safety.
 * Dedup is tested at the bus level (DisruptorEventBus) — the bridge is a
 * pure pass-through serializer.
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

    private record TestEvent(EventMetadata metadata) implements DomainEvent {
        @Override
        public void accept(DomainEventVisitor visitor) { }
    }

    private static TestEvent eventWithId(String eventId) {
        return new TestEvent(new EventMetadata(eventId, 0L, 0L, 0L, "", 1));
    }

    // ── Pass-through behavior (no dedup at bridge level) ────────────────

    @Test
    void sameEventProcessedMultipleTimes() {
        DomainEvent event = eventWithId("evt-001");
        bridge.onDomainEvent(event);
        bridge.onDomainEvent(event);
        // Bridge is pass-through — same event processed twice
        // (unknown event type falls through to default, so eventCount still increments)
        assertEquals(2, bridge.eventCount(),
                "Bridge is pass-through — same event should be processed each time");
    }

    @Test
    void differentEventsBothProcessed() {
        bridge.onDomainEvent(eventWithId("evt-001"));
        bridge.onDomainEvent(eventWithId("evt-002"));
        assertEquals(2, bridge.eventCount());
    }

    // ── Metrics ─────────────────────────────────────────────────────────

    @Test
    void eventCountIsZeroInitially() {
        assertEquals(0, bridge.eventCount());
    }

    @Test
    void eventCountIncrementsForEachEvent() {
        bridge.onDomainEvent(eventWithId("a"));
        assertEquals(1, bridge.eventCount());
        bridge.onDomainEvent(eventWithId("b"));
        assertEquals(2, bridge.eventCount());
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

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

        Thread.sleep(150);

        ArgumentCaptor<GatewayTopic> topicCaptor = ArgumentCaptor.forClass(GatewayTopic.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(router, atLeastOnce()).publish(topicCaptor.capture(), payloadCaptor.capture());

        assertEquals(GatewayTopic.PNL_UPDATE, topicCaptor.getValue());
        assertNotNull(payloadCaptor.getValue());
        assertTrue(payloadCaptor.getValue().length > 0);
    }

    @Test
    void unknownEventTypeIsSilentlyIgnored() {
        bridge.onDomainEvent(eventWithId("unknown-type"));
        verify(router, never()).publish(any(), any(byte[].class));
    }

    @Test
    void eventCountStillIncrementedForUnhandledTypes() {
        bridge.onDomainEvent(eventWithId("unknown-1"));
        bridge.onDomainEvent(eventWithId("unknown-2"));
        assertEquals(2, bridge.eventCount());
    }

    // ── Null safety ─────────────────────────────────────────────────────

    @Test
    void nullEventDoesNotPropagate() {
        bridge.onDomainEvent(null);
        assertEquals(0, bridge.eventCount());
        verify(router, never()).publish(any(), any(byte[].class));
    }
}
