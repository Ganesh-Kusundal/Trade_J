package com.tradej.persistence.oms;

import com.tradej.core.domain.oms.OrderEvent;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for concurrent access to {@link EventSourcedOrderRepository}.
 */
@Tag("stress")
class EventSourcedOrderRepositoryStressTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that concurrent appends for the same orderId don't corrupt
     * the in-memory list (the original ArrayList bug).
     */
    @Test
    void concurrentAppendsSameOrderId() {
        var repo = new EventSourcedOrderRepository(tempDir.resolve("queue"));
        String orderId = "ORD-CONCURRENT-1";
        int workers = 10;
        int appendsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, appendsPerWorker, threadIndex -> {
            repo.append(OrderSubmitted.event(orderId));
        });

        result.assertAllPassed().requireNoExceptions();

        // Verify all events were recorded
        var events = repo.orderEvents(orderId);
        int expected = workers * appendsPerWorker;
        assertEquals(expected, events.size(),
                "Expected " + expected + " events for " + orderId
                        + " but got " + events.size());
    }

    /**
     * Verifies that concurrent appends for different orderIds don't interfere.
     */
    @Test
    void concurrentAppendsDifferentOrderIds() {
        var repo = new EventSourcedOrderRepository(tempDir.resolve("queue2"));
        int workers = 8;
        int appendsPerWorker = 200;

        var result = ConcurrentStressTester.run(workers, appendsPerWorker, threadIndex -> {
            String orderId = "ORD-" + threadIndex;
            repo.append(OrderSubmitted.event(orderId));
        });

        result.assertAllPassed().requireNoExceptions();

        // Each order should have its full event count
        for (int t = 0; t < workers; t++) {
            String orderId = "ORD-" + t;
            assertEquals(appendsPerWorker, repo.orderEvents(orderId).size(),
                    "Order " + orderId + " should have " + appendsPerWorker + " events");
        }
    }

    /**
     * Verifies that concurrent appends followed by rebuild doesn't throw.
     * Uses OrderCancelled which is valid from CANCEL_PENDING state.
     */
    @Test
    void rebuildAfterConcurrentAppends() {
        var repo = new EventSourcedOrderRepository(tempDir.resolve("queue3"));
        String orderId = "ORD-REBUILD-1";
        int workers = 10;
        int appendsPerWorker = 50;

        // Submit OrderSubmitted first (required by state machine)
        repo.append(OrderSubmitted.create(orderId, "SIG-1", "SBIN", 100));

        var result = ConcurrentStressTester.run(workers, appendsPerWorker, threadIndex -> {
            // Each pair: append CancelRequested then OrderCancelled
            // This keeps the state machine in valid transitions
            repo.append(com.tradej.core.domain.oms.CancelRequested.event(orderId));
            repo.append(new com.tradej.core.domain.oms.OrderCancelled(orderId));
        });

        result.assertAllPassed().requireNoExceptions();

        // Total: 1 initial + 2 per worker per iteration
        int expectedTotal = 1 + workers * appendsPerWorker * 2;
        assertEquals(expectedTotal, repo.orderEvents(orderId).size(),
                "Total events should include the initial OrderSubmitted plus pairs");
    }

    /**
     * Verifies that 20 threads appending events for the same 5 orderIds
     * produce complete and ordered event lists.
     */
    @Test
    void concurrentAppendsAcrossMultipleOrderIds() {
        var repo = new EventSourcedOrderRepository(tempDir.resolve("queue4"));
        int workers = 20;
        int appendsPerWorker = 50;

        var result = ConcurrentStressTester.run(workers, appendsPerWorker, threadIndex -> {
            String orderId = "ORD-MULTI-" + (threadIndex % 5);
            repo.append(OrderSubmitted.event(orderId));
        });

        result.assertAllPassed().requireNoExceptions();

        // Each of the 5 orderIds should have (workers / 5) * appendsPerWorker events
        int expectedPerOrder = (workers / 5) * appendsPerWorker;
        for (int i = 0; i < 5; i++) {
            String orderId = "ORD-MULTI-" + i;
            var events = repo.orderEvents(orderId);
            assertEquals(expectedPerOrder, events.size(),
                    "Order " + orderId + " should have " + expectedPerOrder + " events");
            // Verify ordering — each event should be OrderSubmitted, no reordering
            for (int j = 0; j < events.size(); j++) {
                assertEquals(com.tradej.core.domain.oms.OrderEvent.EventType.SUBMITTED,
                        events.get(j).type(),
                        "Event at index " + j + " for " + orderId + " should be SUBMITTED");
            }
        }
    }

    /**
     * Verifies that corrupt entries in Chronicle Queue are counted and skipped
     * while healthy entries are still loaded correctly.
     */
    @Test
    void corruptEntriesAreSkippedDuringLoad() throws Exception {
        Path queuePath = tempDir.resolve("queue5");

        // First session: write valid events
        try (var session = new EventSourcedOrderRepository(queuePath)) {
            session.append(OrderSubmitted.create("ORD-GOOD-1", "SIG-1", "SBIN", 100));
            session.append(OrderSubmitted.create("ORD-GOOD-2", "SIG-2", "TCS", 50));
        }

        // Second session: write corrupt entries directly to Chronicle Queue
        try (net.openhft.chronicle.queue.ChronicleQueue cq =
                     net.openhft.chronicle.queue.ChronicleQueue.singleBuilder(queuePath.toFile()).build()) {
            cq.createAppender().writeText("this is not valid json");
            cq.createAppender().writeText("{\"garbage\": true}");
        }

        // Third session: reopen and verify
        try (var session = new EventSourcedOrderRepository(queuePath)) {
            assertTrue(session.corruptEntryCount() > 0,
                    "Should have detected corrupt entries, got " + session.corruptEntryCount());

            // Valid entries should still be present
            var proj1 = session.rebuild("ORD-GOOD-1");
            assertNotNull(proj1, "ORD-GOOD-1 should be loaded");
            assertEquals("SBIN", proj1.symbol());

            var proj2 = session.rebuild("ORD-GOOD-2");
            assertNotNull(proj2, "ORD-GOOD-2 should be loaded");
            assertEquals("TCS", proj2.symbol());
        }
    }

    /**
     * All entries corrupt — verifies graceful handling and non-zero count.
     */
    @Test
    void allEntriesCorruptHandlesGracefully() throws Exception {
        Path queuePath = tempDir.resolve("queue6");

        // Write only corrupt entries
        try (net.openhft.chronicle.queue.ChronicleQueue cq =
                     net.openhft.chronicle.queue.ChronicleQueue.singleBuilder(queuePath.toFile()).build()) {
            cq.createAppender().writeText("not json");
            cq.createAppender().writeText("also not json");
            cq.createAppender().writeText("{\"broken\": true}");
        }

        // Reopen and verify
        try (var repo = new EventSourcedOrderRepository(queuePath)) {
            assertEquals(3, repo.corruptEntryCount(),
                    "Should have detected all 3 entries as corrupt");
            assertTrue(repo.knownOrderIds().isEmpty(),
                    "No valid orders should be loaded");
        }
    }
}
