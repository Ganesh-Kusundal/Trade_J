package com.tradej.core.domain.reconcile;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ReconciliationPolicyTest {

    @Test
    void autoHaltRequiresMismatchAboveTolerance() {
        ReconciliationPolicy policy = new ReconciliationPolicy(true, 5L);

        ReconciliationDecision decision = policy.evaluate(mismatch(100L, 94L));

        assertTrue(decision.haltRequired());
        assertEquals(6L, decision.mismatchQuantity());
        assertEquals("reconciliation_mismatch", decision.reason());
    }

    @Test
    void toleranceBoundaryDoesNotHalt() {
        ReconciliationPolicy policy = new ReconciliationPolicy(true, 5L);

        ReconciliationDecision decision = policy.evaluate(mismatch(100L, 95L));

        assertFalse(decision.haltRequired());
        assertEquals(5L, decision.mismatchQuantity());
        assertEquals("reconciliation_observed", decision.reason());
    }

    @Test
    void disabledAutoHaltOnlyObservesMismatch() {
        ReconciliationPolicy policy = new ReconciliationPolicy(false, 0L);

        ReconciliationDecision decision = policy.evaluate(mismatch(100L, 0L));

        assertFalse(decision.haltRequired());
        assertEquals(100L, decision.mismatchQuantity());
    }

    @Test
    void negativeToleranceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReconciliationPolicy(true, -1L));
    }

    private static PositionMismatch mismatch(long expected, long broker) {
        return new PositionMismatch(EventMetadata.root(), "SBIN", expected, broker, "oms-reconcile");
    }
}
