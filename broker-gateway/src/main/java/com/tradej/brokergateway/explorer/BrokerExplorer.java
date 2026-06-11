package com.tradej.brokergateway.explorer;

import com.tradej.broker.api.IBrokerConnection;
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
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerSource;

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

        // ── 18 Port Interfaces ──
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("MarketDataProvider", conn.getCapability(MarketDataProvider.class).isPresent());
        capabilities.put("OptionsProvider", conn.getCapability(OptionsProvider.class).isPresent());
        capabilities.put("OrderCommand", conn.getCapability(OrderCommand.class).isPresent());
        capabilities.put("OrderQuery", conn.getCapability(OrderQuery.class).isPresent());
        capabilities.put("PortfolioProvider", conn.getCapability(PortfolioProvider.class).isPresent());
        capabilities.put("MarginProvider", conn.getCapability(MarginProvider.class).isPresent());
        capabilities.put("InstrumentResolver", conn.getCapability(InstrumentResolver.class).isPresent());
        capabilities.put("WebSocketMultiplexer", conn.getCapability(WebSocketMultiplexer.class).isPresent());
        capabilities.put("FuturesProvider", conn.getCapability(FuturesProvider.class).isPresent());
        capabilities.put("BracketOrderProvider", conn.getCapability(BracketOrderProvider.class).isPresent());
        capabilities.put("CoverOrderProvider", conn.getCapability(CoverOrderProvider.class).isPresent());
        capabilities.put("GttOrderProvider", conn.getCapability(GttOrderProvider.class).isPresent());
        capabilities.put("SliceOrderCommand", conn.getCapability(SliceOrderCommand.class).isPresent());
        capabilities.put("SessionRiskProvider", conn.getCapability(SessionRiskProvider.class).isPresent());
        capabilities.put("ConditionalAlertProvider", conn.getCapability(ConditionalAlertProvider.class).isPresent());
        capabilities.put("NewsProvider", conn.getCapability(NewsProvider.class).isPresent());
        capabilities.put("MarketStatusProvider", conn.getCapability(MarketStatusProvider.class).isPresent());

        // ── 6 Capability Marker Interfaces ──
        capabilities.put("OptionsCapable", conn.getCapability(OptionsProvider.class).isPresent());
        capabilities.put("FuturesCapable", conn.getCapability(FuturesProvider.class).isPresent());
        capabilities.put("MarginCapable", conn.getCapability(MarginProvider.class).isPresent());
        capabilities.put("AlertCapable", conn.getCapability(ConditionalAlertProvider.class).isPresent());
        capabilities.put("AdvancedOrderCapable", conn.getCapability(BracketOrderProvider.class).isPresent()
                || conn.getCapability(CoverOrderProvider.class).isPresent()
                || conn.getCapability(GttOrderProvider.class).isPresent()
                || conn.getCapability(SliceOrderCommand.class).isPresent());
        capabilities.put("NewsCapable", conn.getCapability(NewsProvider.class).isPresent());

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
