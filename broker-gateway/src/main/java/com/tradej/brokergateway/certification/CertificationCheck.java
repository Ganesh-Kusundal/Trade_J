package com.tradej.brokergateway.certification;

import java.time.Duration;

/**
 * Result of a single certification check.
 *
 * @param name         check identifier (e.g. "ltp", "quote", "option-chain")
 * @param status       PASS, FAIL, PARTIAL, or SKIP
 * @param evidence     human-readable evidence string (e.g. "ltp=75000")
 * @param latency      time taken for this check
 * @param errorMessage error detail when status is FAIL
 */
public record CertificationCheck(
        String name,
        CertificationStatus status,
        String evidence,
        Duration latency,
        String errorMessage
) {
    public static CertificationCheck pass(String name, String evidence, Duration latency) {
        return new CertificationCheck(name, CertificationStatus.PASS, evidence, latency, null);
    }

    public static CertificationCheck fail(String name, String errorMessage, Duration latency) {
        return new CertificationCheck(name, CertificationStatus.FAIL, null, latency, errorMessage);
    }

    public static CertificationCheck skip(String name, String reason) {
        return new CertificationCheck(name, CertificationStatus.SKIP, reason, Duration.ZERO, null);
    }

    public boolean isPass() {
        return status == CertificationStatus.PASS;
    }

    public boolean isFail() {
        return status == CertificationStatus.FAIL;
    }

    public long latencyMs() {
        return latency.toMillis();
    }
}
