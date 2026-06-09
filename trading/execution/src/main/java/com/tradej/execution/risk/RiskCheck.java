package com.tradej.execution.risk;

/**
 * Single risk check in the chain of responsibility.
 *
 * <p>Each implementation evaluates one aspect of risk (margin, position limits,
 * loss limits, kill switch) and returns a {@link RiskVerdict}. The chain
 * short-circuits on the first rejection.
 *
 * <p>Implementations must be stateless and thread-safe.
 */
@FunctionalInterface
public interface RiskCheck {

    /**
     * Evaluate this risk check against the given context.
     *
     * @param context current risk state
     * @return verdict — approved or rejected with reason
     */
    RiskVerdict check(RiskContext context);
}
