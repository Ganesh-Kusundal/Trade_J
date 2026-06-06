package com.tradej.brokergateway.spi;

import com.tradej.brokergateway.result.BrokerSource;

import java.util.List;
import java.util.Map;

/**
 * Declares a broker's capabilities, supported segments, and metadata.
 *
 * <p>Capabilities are keyed by port interface name (e.g., "MarketDataProvider", "BracketOrderProvider")
 * with boolean values indicating support.
 *
 * <p>Example for Dhan:
 * <pre>
 *   capabilities = {
 *     "MarketDataProvider": true,
 *     "BracketOrderProvider": true,
 *     "NewsProvider": false,
 *     ...
 *   }
 * </pre>
 */
public record BrokerDescriptor(
        BrokerSource source,
        String displayName,
        Map<String, Boolean> capabilities,
        Map<String, String> metadata,
        List<String> supportedSegments,
        String rateLimitInfo,
        Map<String, CapabilityMetadata> capabilityMetadata
) {
    /**
     * Backward-compatible constructor without capabilityMetadata.
     */
    public BrokerDescriptor(
            BrokerSource source,
            String displayName,
            Map<String, Boolean> capabilities,
            Map<String, String> metadata,
            List<String> supportedSegments,
            String rateLimitInfo
    ) {
        this(source, displayName, capabilities, metadata, supportedSegments, rateLimitInfo, Map.of());
    }

    public BrokerDescriptor {
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        supportedSegments = supportedSegments == null ? List.of() : List.copyOf(supportedSegments);
        capabilityMetadata = capabilityMetadata == null ? Map.of() : Map.copyOf(capabilityMetadata);
    }

    /**
     * Check if a specific capability is supported.
     */
    public boolean supports(String capability) {
        return Boolean.TRUE.equals(capabilities.get(capability));
    }

    /**
     * Count of supported capabilities.
     */
    public int supportedCount() {
        return (int) capabilities.values().stream().filter(Boolean::booleanValue).count();
    }

    /**
     * Total number of capabilities checked.
     */
    public int totalCount() {
        return capabilities.size();
    }

    /**
     * Returns the {@link CapabilityMetadata} for the given capability key,
     * or a default empty metadata if none is registered.
     */
    public CapabilityMetadata metadataFor(String capability) {
        return capabilityMetadata.getOrDefault(capability, new CapabilityMetadata("", "other", "1.0"));
    }
}
