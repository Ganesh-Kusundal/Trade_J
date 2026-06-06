package com.tradej.brokergateway.explorer;

import com.tradej.brokergateway.result.BrokerSource;

import java.util.List;
import java.util.Map;

/**
 * Result of a broker inspection — includes both static capability checks and live probe results.
 *
 * @param source          which broker was inspected
 * @param capabilities    static capability checks (from getCapability())
 * @param probes          live probe results (from actual broker calls)
 * @param metadata        key-value metadata (environment, rate limits, etc.)
 * @param catalogLoaded   whether the instrument catalog is loaded
 * @param instrumentCount number of instruments in the catalog
 */
public record BrokerInspectionReport(
        BrokerSource source,
        Map<String, Boolean> capabilities,
        List<CapabilityProbe> probes,
        Map<String, String> metadata,
        boolean catalogLoaded,
        int instrumentCount
) {
    /**
     * Backward-compatible constructor without probes.
     */
    public BrokerInspectionReport(
            BrokerSource source,
            Map<String, Boolean> capabilities,
            Map<String, String> metadata,
            boolean catalogLoaded,
            int instrumentCount
    ) {
        this(source, capabilities, List.of(), metadata, catalogLoaded, instrumentCount);
    }

    public BrokerInspectionReport {
        capabilities = Map.copyOf(capabilities);
        probes = probes == null ? List.of() : List.copyOf(probes);
        metadata = Map.copyOf(metadata);
    }

    public int supportedCount() {
        return (int) capabilities.values().stream().filter(Boolean::booleanValue).count();
    }

    public int totalCount() {
        return capabilities.size();
    }

    public int probesPassedCount() {
        return (int) probes.stream().filter(CapabilityProbe::isPass).count();
    }

    public int probesFailedCount() {
        return (int) probes.stream().filter(CapabilityProbe::isFail).count();
    }

    public int probesTotalCount() {
        return probes.size();
    }
}
