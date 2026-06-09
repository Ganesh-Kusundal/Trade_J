package com.tradej.brokergateway.certification;

import com.tradej.brokergateway.result.BrokerSource;

import java.time.Instant;

/**
 * A single certification artifact — evidence of a broker endpoint check.
 * Stored to {@code CertificationArtifacts/<broker>/<category>/<check>.json}.
 */
public record CertificationArtifact(
        BrokerSource broker,
        String checkName,
        String category,
        CertificationStatus status,
        String evidence,
        long latencyMs,
        Instant certifiedAt,
        String errorMessage
) {

    public static CertificationArtifact fromCheck(BrokerSource broker, CertificationCheck check, String category) {
        return new CertificationArtifact(
                broker,
                check.name(),
                category,
                check.status(),
                check.evidence(),
                check.latencyMs(),
                Instant.now(),
                check.errorMessage()
        );
    }

    public boolean isPass() {
        return status == CertificationStatus.PASS;
    }

    public String toSummaryLine() {
        String icon = isPass() ? "PASS" : "FAIL";
        return String.format("[%s] %-28s %-12s %dms  %s",
                icon, checkName, category, latencyMs,
                isPass() ? (evidence != null ? evidence : "") : (errorMessage != null ? errorMessage : ""));
    }
}
