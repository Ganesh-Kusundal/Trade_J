package com.tradej.broker.api.spi;

import java.util.List;
import java.util.Map;

/**
 * Declares a broker's capabilities, supported segments, metadata, and credential requirements.
 *
 * <p>Capabilities are keyed by port interface name (e.g., "MarketDataProvider", "BracketOrderProvider")
 * with boolean values indicating support.
 *
 * <p>Credential fields declare what authentication inputs the broker requires,
 * enabling the frontend to dynamically render credential forms.
 */
public record BrokerDescriptor(
        BrokerSource source,
        String displayName,
        Map<String, Boolean> capabilities,
        Map<String, String> metadata,
        List<String> supportedSegments,
        String rateLimitInfo,
        Map<String, CapabilityMetadata> capabilityMetadata,
        List<CredentialField> credentialFields
) {
    /**
     * Backward-compatible constructor without capabilityMetadata or credentialFields.
     */
    public BrokerDescriptor(
            BrokerSource source,
            String displayName,
            Map<String, Boolean> capabilities,
            Map<String, String> metadata,
            List<String> supportedSegments,
            String rateLimitInfo
    ) {
        this(source, displayName, capabilities, metadata, supportedSegments, rateLimitInfo, Map.of(), List.of());
    }

    /**
     * Backward-compatible constructor without credentialFields.
     */
    public BrokerDescriptor(
            BrokerSource source,
            String displayName,
            Map<String, Boolean> capabilities,
            Map<String, String> metadata,
            List<String> supportedSegments,
            String rateLimitInfo,
            Map<String, CapabilityMetadata> capabilityMetadata
    ) {
        this(source, displayName, capabilities, metadata, supportedSegments, rateLimitInfo, capabilityMetadata, List.of());
    }

    public BrokerDescriptor {
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        supportedSegments = supportedSegments == null ? List.of() : List.copyOf(supportedSegments);
        capabilityMetadata = capabilityMetadata == null ? Map.of() : Map.copyOf(capabilityMetadata);
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
    }

    public boolean supports(String capability) {
        return Boolean.TRUE.equals(capabilities.get(capability));
    }

    public int supportedCount() {
        return (int) capabilities.values().stream().filter(Boolean::booleanValue).count();
    }

    public int totalCount() {
        return capabilities.size();
    }

    public CapabilityMetadata metadataFor(String capability) {
        return capabilityMetadata.getOrDefault(capability, new CapabilityMetadata("", "other", "1.0"));
    }
}
