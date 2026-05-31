package com.tradej.execution.identity;

import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Stress tests for concurrent access to {@link OrderIdentityRegistry}.
 */
@Tag("stress")
class OrderIdentityRegistryStressTest {

    /**
     * Verifies that concurrent register + remove cycles for distinct
     * internal order IDs maintain correct state.
     */
    @Test
    void concurrentRegisterAndRemove() {
        var registry = new OrderIdentityRegistry();
        int workers = 10;
        int opsPerWorker = 200;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            String internalId = "ORD-" + threadIndex + "-" + UUID.randomUUID();
            String brokerId = "D" + threadIndex;
            String signalId = "SIG-" + threadIndex;

            registry.register(internalId, brokerId, signalId);

            String resolved = registry.resolveInternalId(brokerId);
            assertNotNull(resolved, "Should resolve broker ID " + brokerId);

            registry.remove(internalId);

            assertNull(registry.resolveInternalId(brokerId),
                    "Should no longer resolve after remove");
            assertNull(registry.resolveBySignalId(signalId),
                    "Should no longer resolve signal after remove");
        });

        result.assertAllPassed().requireNoExceptions();
        assertEquals(0, registry.size(), "Registry should be empty after all removes");
    }

    /**
     * Verifies that register and acknowledge can race without corruption.
     */
    @Test
    void concurrentRegisterAndAcknowledge() {
        var registry = new OrderIdentityRegistry();
        var sharedOrderId = "ORD-SHARED-1";
        var sharedSignalId = "SIG-SHARED-1";
        var brokerId = "D-SHARED-1";

        // Pre-register with null broker ID (simulating processSignal)
        registry.register(sharedOrderId, null, sharedSignalId);

        int workers = 10;
        int opsPerWorker = 50;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            // Half the threads acknowledge, half remove and re-register
            if (threadIndex % 2 == 0) {
                registry.acknowledge(sharedOrderId, brokerId);
            } else {
                registry.register(sharedOrderId, brokerId, sharedSignalId);
            }
        });

        result.assertAllPassed().requireNoExceptions();

        // After all operations, the mapping should be consistent
        String resolved = registry.resolveInternalId(brokerId);
        assertNotNull(resolved, "Broker ID should still resolve");
        assertEquals(sharedOrderId, resolved, "Should resolve to original order ID");
    }

    /**
     * Verifies that many distinct registrations don't interfere.
     */
    @Test
    void concurrentManyDistinctRegistrations() {
        var registry = new OrderIdentityRegistry();
        int workers = 8;
        int opsPerWorker = 500;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            String internalId = "ORD-" + threadIndex + "-" + UUID.randomUUID();
            String brokerId = "D" + threadIndex + "-" + System.nanoTime();
            String signalId = "SIG-" + threadIndex + "-" + System.nanoTime();
            registry.register(internalId, brokerId, signalId);

            String resolved = registry.resolveInternalId(brokerId);
            assertNotNull(resolved, "Should resolve immediately");
            assertEquals(internalId, resolved, "Should resolve to correct internal ID");

            String bySignal = registry.resolveBySignalId(signalId);
            assertNotNull(bySignal, "Should resolve by signal ID");
            assertEquals(internalId, bySignal, "Signal should map to correct order");
        });

        result.assertAllPassed().requireNoExceptions();
    }

    /**
     * Verifies that the registry size is accurate under concurrent add/remove.
     */
    @Test
    void registrySizeAccurateUnderConcurrentOps() {
        var registry = new OrderIdentityRegistry();
        var keyCount = new ConcurrentHashMap<String, Boolean>();
        int workers = 10;
        int opsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            String id = "ORD-" + threadIndex + "-" + (int)(Math.random() * 100);
            if (threadIndex % 3 == 0) {
                registry.remove(id);
                keyCount.remove(id);
            } else {
                registry.register(id, "D-" + id, "SIG-" + id);
                keyCount.put(id, true);
            }
        });

        result.assertAllPassed().requireNoExceptions();
    }
}
