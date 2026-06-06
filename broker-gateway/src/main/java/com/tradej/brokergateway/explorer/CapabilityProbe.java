package com.tradej.brokergateway.explorer;

import java.time.Duration;

/**
 * Result of a single capability probe — a live call to a broker method.
 *
 * @param name     probe identifier (e.g. "quote", "option-chain", "balance")
 * @param status   PASS, FAIL, SKIP, or TIMEOUT
 * @param latency  time taken for the probe call
 * @param evidence human-readable evidence (e.g. "ltp=75000" or error message)
 * @param detail   additional detail (optional)
 */
public record CapabilityProbe(
        String name,
        ProbeStatus status,
        Duration latency,
        String evidence,
        String detail
) {
    public static CapabilityProbe pass(String name, String evidence, Duration latency) {
        return new CapabilityProbe(name, ProbeStatus.PASS, latency, evidence, null);
    }

    public static CapabilityProbe fail(String name, String errorMessage, Duration latency) {
        return new CapabilityProbe(name, ProbeStatus.FAIL, latency, null, errorMessage);
    }

    public static CapabilityProbe skip(String name, String reason) {
        return new CapabilityProbe(name, ProbeStatus.SKIP, Duration.ZERO, null, reason);
    }

    public long latencyMs() {
        return latency.toMillis();
    }

    public boolean isPass() {
        return status == ProbeStatus.PASS;
    }

    public boolean isFail() {
        return status == ProbeStatus.FAIL;
    }
}
