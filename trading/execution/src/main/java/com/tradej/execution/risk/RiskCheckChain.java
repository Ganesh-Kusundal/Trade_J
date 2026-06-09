package com.tradej.execution.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Composes an ordered chain of {@link RiskCheck} implementations.
 *
 * <p>Evaluates checks sequentially and short-circuits on the first rejection.
 * Returns the rejecting verdict, or an approved verdict if all checks pass.
 *
 * <p>Usage:
 * <pre>
 *   var chain = new RiskCheckChain(List.of(
 *       new KillSwitchRiskCheck(),
 *       new DailyLossRiskCheck(),
 *       new PositionLimitRiskCheck()
 *   ));
 *   Optional&lt;RiskVerdict&gt; rejection = chain.findRejection(context);
 * </pre>
 */
public final class RiskCheckChain {

    private static final Logger log = LoggerFactory.getLogger(RiskCheckChain.class);

    private final List<RiskCheck> checks;

    public RiskCheckChain(List<RiskCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    /**
     * Run all checks. Returns the first rejection, or empty if all pass.
     */
    public Optional<RiskVerdict> findRejection(RiskContext context) {
        for (RiskCheck check : checks) {
            RiskVerdict verdict = check.check(context);
            if (!verdict.approved()) {
                log.debug("Risk check rejected: {} — {}", verdict.checkName(), verdict.reason());
                return Optional.of(verdict);
            }
        }
        return Optional.empty();
    }

    /**
     * Run all checks. Returns true if all pass.
     */
    public boolean isApproved(RiskContext context) {
        return findRejection(context).isEmpty();
    }

    public int size() {
        return checks.size();
    }
}
