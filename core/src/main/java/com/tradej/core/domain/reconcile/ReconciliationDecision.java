package com.tradej.core.domain.reconcile;

/**
 * Explicit outcome of evaluating a reconciliation mismatch.
 */
public record ReconciliationDecision(
        boolean haltRequired,
        long mismatchQuantity,
        String reason
) {
}
