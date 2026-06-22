package com.tradej.core.domain.reconcile;

import com.tradej.core.domain.event.PositionMismatch;

import java.util.Objects;

/**
 * Runtime contract for deciding when reconciliation must halt trading.
 */
public record ReconciliationPolicy(
        boolean autoHalt,
        long mismatchToleranceQty
) {

    public ReconciliationPolicy {
        if (mismatchToleranceQty < 0) {
            throw new IllegalArgumentException("mismatchToleranceQty must be non-negative");
        }
    }

    public ReconciliationDecision evaluate(PositionMismatch mismatch) {
        Objects.requireNonNull(mismatch, "mismatch");
        long mismatchQuantity = Math.abs(mismatch.paperQuantity() - mismatch.brokerQuantity());
        boolean haltRequired = autoHalt && mismatchQuantity > mismatchToleranceQty;
        String reason = haltRequired ? "reconciliation_mismatch" : "reconciliation_observed";
        return new ReconciliationDecision(haltRequired, mismatchQuantity, reason);
    }
}
