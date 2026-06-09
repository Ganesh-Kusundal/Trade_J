package com.tradej.execution.risk;

/**
 * Result of a single risk check in the chain of responsibility.
 *
 * @param approved true if the check passed, false if it rejected the signal
 * @param checkName identifying name of the check (e.g. "margin", "position_limit")
 * @param reason human-readable reason for rejection (empty if approved)
 */
public record RiskVerdict(boolean approved, String checkName, String reason) {

    public static RiskVerdict approve(String checkName) {
        return new RiskVerdict(true, checkName, "");
    }

    public static RiskVerdict reject(String checkName, String reason) {
        return new RiskVerdict(false, checkName, reason);
    }
}
