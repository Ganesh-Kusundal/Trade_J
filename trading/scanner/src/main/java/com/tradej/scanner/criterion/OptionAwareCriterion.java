package com.tradej.scanner.criterion;

/**
 * Marker interface for {@link ScanCriterion} implementations that require
 * option chain data (e.g., PCR, OI-based criteria).
 *
 * <p>Replaces the fragile string-based type check in
 * {@link CriterionGroup#requiresOptionChain()} and
 * {@link com.tradej.scanner.engine.ScanEngine#hasOptionCriteria(ScanCriterion)}.
 * Criteria that need option chain data should implement this interface so the
 * scan engine can determine whether a fine pass with option chain fetch is
 * needed without relying on string containment checks (fixes S-06).
 */
public interface OptionAwareCriterion {
    /** Returns true if this criterion requires option chain snapshot data. */
    boolean requiresOptionChain();
}
