package com.tradej.brokergateway.certification;

import com.tradej.brokergateway.result.BrokerSource;

import java.time.Duration;
import java.util.List;

/**
 * Complete certification report for a broker.
 *
 * @param broker       which broker was certified
 * @param checks       individual check results
 * @param overall      PASS if all passed, FAIL if all failed, PARTIAL otherwise
 * @param totalLatency sum of all check latencies
 */
public record CertificationReport(
        BrokerSource broker,
        List<CertificationCheck> checks,
        CertificationStatus overall,
        Duration totalLatency
) {
    public CertificationReport {
        checks = List.copyOf(checks);
    }

    public int passed() {
        return (int) checks.stream().filter(CertificationCheck::isPass).count();
    }

    public int failed() {
        return (int) checks.stream().filter(CertificationCheck::isFail).count();
    }

    public int skipped() {
        return (int) checks.stream().filter(c -> c.status() == CertificationStatus.SKIP).count();
    }

    public int total() {
        return checks.size();
    }

    public boolean isPass() {
        return overall == CertificationStatus.PASS;
    }

    public long totalLatencyMs() {
        return totalLatency.toMillis();
    }

    /**
     * Format a human-readable report.
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Broker Certification: ").append(broker).append(" ===\n\n");
        sb.append(String.format("  %-28s %-10s %-10s %s%n", "Check", "Status", "Latency", "Evidence"));
        sb.append("  ").append("─".repeat(72)).append("\n");
        for (CertificationCheck check : checks) {
            sb.append(String.format("  %-28s %-10s %-10s %s%n",
                    check.name(),
                    check.status(),
                    check.latencyMs() + "ms",
                    check.isPass() ? check.evidence() : (check.errorMessage() != null ? check.errorMessage() : "")));
        }
        sb.append("\n");
        sb.append(String.format("  Result: %s (%d/%d passed", overall, passed(), total()));
        if (skipped() > 0) {
            sb.append(", ").append(skipped()).append(" skipped");
        }
        sb.append(")\n");
        sb.append(String.format("  Total latency: %dms%n", totalLatencyMs()));
        return sb.toString();
    }
}
