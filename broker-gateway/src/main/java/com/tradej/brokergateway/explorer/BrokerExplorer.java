package com.tradej.brokergateway.explorer;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.BrokerCapabilityRouter;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarketStatusProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inspects a broker's capabilities, health, and metadata.
 * Detects all 18 port interfaces and 6 capability marker interfaces.
 */
public final class BrokerExplorer {

    private BrokerExplorer() {
    }

    /**
     * Inspect a broker and produce a capability report.
     */
    public static BrokerInspectionReport inspect(BrokerHandle broker) {
        IBrokerConnection conn = broker.connection();
        BrokerCapabilityRouter capabilityRouter = BrokerCapabilityRouter.named(broker.source().name(), conn);

        // ── 18 Port Interfaces ──
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("MarketDataProvider", capabilityRouter.supports(MarketDataProvider.class));
        capabilities.put("OptionsProvider", capabilityRouter.supports(OptionsProvider.class));
        capabilities.put("OrderCommand", capabilityRouter.supports(OrderCommand.class));
        capabilities.put("OrderQuery", capabilityRouter.supports(OrderQuery.class));
        capabilities.put("PortfolioProvider", capabilityRouter.supports(PortfolioProvider.class));
        capabilities.put("MarginProvider", capabilityRouter.supports(MarginProvider.class));
        capabilities.put("InstrumentResolver", capabilityRouter.supports(InstrumentResolver.class));
        capabilities.put("WebSocketMultiplexer", capabilityRouter.supports(WebSocketMultiplexer.class));
        capabilities.put("FuturesProvider", capabilityRouter.supports(FuturesProvider.class));
        capabilities.put("BracketOrderProvider", capabilityRouter.supports(BracketOrderProvider.class));
        capabilities.put("CoverOrderProvider", capabilityRouter.supports(CoverOrderProvider.class));
        capabilities.put("GttOrderProvider", capabilityRouter.supports(GttOrderProvider.class));
        capabilities.put("SliceOrderCommand", capabilityRouter.supports(SliceOrderCommand.class));
        capabilities.put("SessionRiskProvider", capabilityRouter.supports(SessionRiskProvider.class));
        capabilities.put("ConditionalAlertProvider", capabilityRouter.supports(ConditionalAlertProvider.class));
        capabilities.put("NewsProvider", capabilityRouter.supports(NewsProvider.class));
        capabilities.put("MarketStatusProvider", capabilityRouter.supports(MarketStatusProvider.class));

        // ── 6 Capability Marker Interfaces ──
        capabilities.put("OptionsCapable", capabilityRouter.supports(OptionsProvider.class));
        capabilities.put("FuturesCapable", capabilityRouter.supports(FuturesProvider.class));
        capabilities.put("MarginCapable", capabilityRouter.supports(MarginProvider.class));
        capabilities.put("AlertCapable", capabilityRouter.supports(ConditionalAlertProvider.class));
        capabilities.put("AdvancedOrderCapable", capabilityRouter.supports(BracketOrderProvider.class)
                || capabilityRouter.supports(CoverOrderProvider.class)
                || capabilityRouter.supports(GttOrderProvider.class)
                || capabilityRouter.supports(SliceOrderCommand.class));
        capabilities.put("NewsCapable", capabilityRouter.supports(NewsProvider.class));

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("broker", broker.source().name());
        metadata.put("instruments", String.valueOf(conn.instruments().catalogSize()));
        metadata.put("catalogLoaded", String.valueOf(conn.instruments().isLoaded()));

        return new BrokerInspectionReport(
                broker.source(),
                capabilities,
                metadata,
                conn.instruments().isLoaded(),
                conn.instruments().catalogSize()
        );
    }

    /**
     * Inspect capabilities from a BrokerDescriptor without requiring a live connection.
     * This is the static counterpart to inspect(BrokerHandle) which requires a live connection.
     */
    public static BrokerInspectionReport inspectDescriptor(BrokerDescriptor descriptor) {
        Map<String, Boolean> capabilities = new LinkedHashMap<>(descriptor.capabilities());

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("broker", descriptor.source().name());
        metadata.put("displayName", descriptor.displayName());
        metadata.put("segments", String.join(", ", descriptor.supportedSegments()));
        metadata.put("rateLimits", descriptor.rateLimitInfo());
        if (descriptor.capabilityMetadata() != null) {
            metadata.put("capabilityMetadataCount", String.valueOf(descriptor.capabilityMetadata().size()));
        }

        return new BrokerInspectionReport(
                descriptor.source(),
                capabilities,
                metadata,
                false,
                0
        );
    }

    /**
     * Format a human-readable inspection report.
     */
    public static String formatReport(BrokerInspectionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Broker Inspector: ").append(report.source()).append(" ===\n\n");
        sb.append("  Port Interfaces:\n");
        int portCount = 0;
        for (Map.Entry<String, Boolean> entry : report.capabilities().entrySet()) {
            if (portCount >= 15) break;
            String icon = entry.getValue() ? "✓" : "✗";
            sb.append(String.format("    %s %s%n", icon, entry.getKey()));
            portCount++;
        }
        sb.append("\n  Capability Markers:\n");
        int markerCount = 0;
        for (Map.Entry<String, Boolean> entry : report.capabilities().entrySet()) {
            if (markerCount < 15) { markerCount++; continue; }
            String icon = entry.getValue() ? "✓" : "✗";
            sb.append(String.format("    %s %s%n", icon, entry.getKey()));
            markerCount++;
        }
        sb.append("\n  Metadata:\n");
        for (Map.Entry<String, String> entry : report.metadata().entrySet()) {
            sb.append(String.format("    %-20s %s%n", entry.getKey() + ":", entry.getValue()));
        }
        sb.append("\n  Summary: ").append(report.supportedCount())
                .append("/").append(report.totalCount()).append(" capabilities supported\n");
        sb.append("\n");
        return sb.toString();
    }
}
