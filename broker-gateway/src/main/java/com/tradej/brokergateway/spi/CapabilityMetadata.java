package com.tradej.brokergateway.spi;

/**
 * Metadata about a single broker capability.
 *
 * @param description  human-readable description of what this capability does
 * @param category     grouping: market, orders, portfolio, streaming, services, risk, marker
 * @param version      API version (e.g. "1.0")
 */
public record CapabilityMetadata(
        String description,
        String category,
        String version
) {
    public CapabilityMetadata {
        if (description == null) description = "";
        if (category == null) category = "other";
        if (version == null) version = "1.0";
    }
}
