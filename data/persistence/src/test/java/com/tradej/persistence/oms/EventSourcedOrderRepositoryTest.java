package com.tradej.persistence.oms;

import com.tradej.core.domain.oms.CancelRequested;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderCancelled;
import com.tradej.core.domain.oms.OrderEvent;
import com.tradej.core.domain.oms.OrderExpired;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderPartiallyFilled;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.oms.OrderRejected;
import com.tradej.core.domain.oms.OrderStateMachine;
import com.tradej.core.domain.oms.OrderSubmitted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class EventSourcedOrderRepositoryTest {

    private Path tempDir;
    private EventSourcedOrderRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("oms-repo-test-");
        repo = new EventSourcedOrderRepository(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        repo.close();
        // Clean up Chronicle Queue files
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void appendsAndRebuildsFullLifecycle() {
        repo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        repo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        repo.append(OrderPartiallyFilled.event("ORD-1", 25, 150_00L));
        repo.append(OrderFullyFilled.event("ORD-1", 100, 150_50L));

        OrderProjection proj = repo.rebuild("ORD-1");
        assertNotNull(proj);
        assertEquals("ORD-1", proj.orderId());
        assertEquals("SBIN", proj.symbol());
        assertEquals(100, proj.filledQuantity());
        assertEquals(com.tradej.core.domain.oms.LifecycleState.FILLED, proj.status());
    }

    @Test
    void rebuildReturnsNullForUnknownOrder() {
        assertNull(repo.rebuild("UNKNOWN-ORDER"));
    }

    @Test
    void appendAllRebuildsCorrectly() {
        List<OrderEvent> events = List.of(
                OrderSubmitted.create("ORD-2", "sig-2", "TCS", 50),
                OrderAcknowledged.event("ORD-2", "EX-002"),
                OrderFullyFilled.event("ORD-2", 50, 3_200_00L)
        );
        repo.appendAll(events);

        OrderProjection proj = repo.rebuild("ORD-2");
        assertNotNull(proj);
        assertEquals("TCS", proj.symbol());
        assertEquals(50, proj.totalQuantity());
        assertEquals(50, proj.filledQuantity());
        assertEquals(3_200_00L, proj.averagePricePaisa());
    }

    @Test
    void rebuildStateMachineReturnsFullMachine() {
        repo.append(OrderSubmitted.create("ORD-3", "sig-3", "RELIANCE", 75));
        repo.append(OrderAcknowledged.event("ORD-3", "EX-003"));
        repo.append(OrderPartiallyFilled.event("ORD-3", 30, 2_500_00L));

        OrderStateMachine sm = repo.rebuildStateMachine("ORD-3");
        assertNotNull(sm);
        var projection = sm.toProjection();
        assertEquals(com.tradej.core.domain.oms.LifecycleState.PARTIALLY_FILLED, projection.status());
        assertEquals(30, projection.filledQuantity());
        assertEquals(2_500_00L, sm.averagePricePaisa());
    }

    @Test
    void knownOrderIdsReturnsAllOrderIds() {
        repo.append(OrderSubmitted.create("ORD-A", "s1", "SBIN", 10));
        repo.append(OrderSubmitted.create("ORD-B", "s2", "TCS", 20));
        repo.append(OrderSubmitted.create("ORD-C", "s3", "HDFC", 30));

        List<String> ids = repo.knownOrderIds();
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of("ORD-A", "ORD-B", "ORD-C")));
    }

    @Test
    void activeCountReportsNonFinalOrders() {
        repo.append(OrderSubmitted.create("ORD-1", "s1", "SBIN", 100));
        repo.append(OrderAcknowledged.event("ORD-1", "EX-1"));
        // ORD-1 is SUBMITTED → active

        repo.append(OrderSubmitted.create("ORD-2", "s2", "TCS", 50));
        repo.append(OrderAcknowledged.event("ORD-2", "EX-2"));
        repo.append(OrderFullyFilled.event("ORD-2", 50, 3_200_00L));
        // ORD-2 is FILLED → not active

        assertEquals(1, repo.activeCount());
    }

    @Test
    void rejectedCountReportsCorrectly() {
        repo.append(OrderSubmitted.create("ORD-1", "s1", "SBIN", 100));
        repo.append(OrderRejected.event("ORD-1", "Insufficient margin"));

        repo.append(OrderSubmitted.create("ORD-2", "s2", "TCS", 50));
        repo.append(OrderAcknowledged.event("ORD-2", "EX-2"));
        repo.append(OrderFullyFilled.event("ORD-2", 50, 3_200_00L));

        assertEquals(1, repo.rejectedCount());
    }

    // ── Serialization roundtrip for all event types ─────────────────────────

    @Test
    void serializationRoundtripForAllEventTypes() {
        // Append all event types for the same order
        repo.append(OrderSubmitted.create("ORD-SER", "sig", "SBIN", 100));
        repo.append(OrderAcknowledged.event("ORD-SER", "EX-SER"));
        repo.append(OrderPartiallyFilled.event("ORD-SER", 25, 150_00L));
        repo.append(CancelRequested.event("ORD-SER"));
        repo.append(OrderCancelled.event("ORD-SER"));

        OrderProjection proj = repo.rebuild("ORD-SER");
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.CANCELLED, proj.status());
    }

    @Test
    void serializationRoundtripRejectedOrder() {
        repo.append(OrderSubmitted.create("ORD-REJ", "sig", "TCS", 50));
        repo.append(OrderRejected.event("ORD-REJ", "Price band violation"));

        OrderProjection proj = repo.rebuild("ORD-REJ");
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.REJECTED, proj.status());
    }

    @Test
    void serializationRoundtripExpiredOrder() {
        repo.append(OrderSubmitted.create("ORD-EXP", "sig", "HDFC", 20));
        repo.append(OrderAcknowledged.event("ORD-EXP", "EX-EXP"));
        repo.append(OrderExpired.event("ORD-EXP"));

        OrderProjection proj = repo.rebuild("ORD-EXP");
        assertNotNull(proj);
        assertEquals(com.tradej.core.domain.oms.LifecycleState.EXPIRED, proj.status());
    }

    @Test
    void repositoryLoadsExistingDataOnConstruction() throws IOException {
        // First session: add data and close
        try (EventSourcedOrderRepository session1 = new EventSourcedOrderRepository(tempDir)) {
            session1.append(OrderSubmitted.create("ORD-PERSIST", "sig", "SBIN", 100));
            session1.append(OrderAcknowledged.event("ORD-PERSIST", "EX-PERSIST"));
            session1.append(OrderFullyFilled.event("ORD-PERSIST", 100, 150_00L));
        }

        // Second session: verify data was persisted to Chronicle Queue
        try (EventSourcedOrderRepository session2 = new EventSourcedOrderRepository(tempDir)) {
            OrderProjection proj = session2.rebuild("ORD-PERSIST");
            assertNotNull(proj);
            assertEquals("SBIN", proj.symbol());
            assertEquals(100, proj.filledQuantity());
            assertEquals(com.tradej.core.domain.oms.LifecycleState.FILLED, proj.status());
        }
    }
}
