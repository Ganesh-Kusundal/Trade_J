package com.tradej.broker.api.spi;

/**
 * Metadata for a specific broker capability.
 *
 * @param description Human-readable description of what the capability provides
 * @param category Capability category (market, orders, portfolio, risk, services, streaming)
 * @param version Semantic version of this capability implementation
 */
public record CapabilityMetadata(
        String description,
        String category,
        String version
) {
    public CapabilityMetadata {
        description = description == null ? "" : description;
        category = category == null ? "other" : category;
        version = version == null ? "1.0" : version;
    }
}
