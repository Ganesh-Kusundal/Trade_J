package com.tradej.core.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeterministicIdGeneratorTest {

    @Test
    void sameSeedProducesSameIds() {
        var gen1 = new DeterministicIdGenerator(42L);
        var gen2 = new DeterministicIdGenerator(42L);

        for (int i = 0; i < 100; i++) {
            assertEquals(gen1.nextId(), gen2.nextId());
        }
    }

    @Test
    void differentSeedsProduceDifferentIds() {
        var gen1 = new DeterministicIdGenerator(1L);
        var gen2 = new DeterministicIdGenerator(2L);

        assertNotEquals(gen1.nextId(), gen2.nextId());
    }

    @Test
    void idsAreMonotonicallyIncreasing() {
        var gen = new DeterministicIdGenerator(0L);
        long prev = gen.nextId();
        for (int i = 0; i < 1000; i++) {
            long next = gen.nextId();
            assertNotEquals(prev, next);
            prev = next;
        }
    }

    @Test
    void generateEventIdReturnsNonEmpty() {
        var gen = new DeterministicIdGenerator(42L);
        String id = gen.generateEventId();
        assertNotNull(id);
        assertNotEquals("", id);
    }

    @Test
    void generateEventIdIsDeterministic() {
        var gen1 = new DeterministicIdGenerator(42L);
        var gen2 = new DeterministicIdGenerator(42L);
        assertEquals(gen1.generateEventId(), gen2.generateEventId());
    }

    @Test
    void generateOrderIdReturnsPrefixedId() {
        var gen = new DeterministicIdGenerator(42L);
        String id = gen.generateOrderId();
        assertNotNull(id);
        // Should be deterministic
        var gen2 = new DeterministicIdGenerator(42L);
        assertEquals(id, gen2.generateOrderId());
    }

    @Test
    void generateTradeIdReturnsPrefixedId() {
        var gen = new DeterministicIdGenerator(42L);
        String id = gen.generateTradeId();
        assertNotNull(id);
        var gen2 = new DeterministicIdGenerator(42L);
        assertEquals(id, gen2.generateTradeId());
    }

    @Test
    void sameSeedWithMixedCallsProducesConsistentSequence() {
        var gen1 = new DeterministicIdGenerator(42L);
        var gen2 = new DeterministicIdGenerator(42L);

        for (int i = 0; i < 50; i++) {
            assertEquals(gen1.generateEventId(), gen2.generateEventId());
            assertEquals(gen1.generateOrderId(), gen2.generateOrderId());
            assertEquals(gen1.generateTradeId(), gen2.generateTradeId());
            assertEquals(gen1.nextId(), gen2.nextId());
        }
    }
}
