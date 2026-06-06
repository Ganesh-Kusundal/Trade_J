package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.brokergateway.explorer.BrokerExplorer;
import com.tradej.brokergateway.explorer.BrokerInspectionReport;
import com.tradej.brokergateway.result.BrokerSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerExplorerTest {

    @Test
    void inspectDetectsAvailableCapabilities() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);
        when(resolver.catalogSize()).thenReturn(85000);
        when(resolver.isLoaded()).thenReturn(true);

        when(conn.getCapability(MarketDataProvider.class)).thenReturn(Optional.of(mock(MarketDataProvider.class)));
        when(conn.getCapability(OptionsProvider.class)).thenReturn(Optional.of(mock(OptionsProvider.class)));
        when(conn.getCapability(PortfolioProvider.class)).thenReturn(Optional.of(mock(PortfolioProvider.class)));
        when(conn.getCapability(OrderQuery.class)).thenReturn(Optional.of(mock(OrderQuery.class)));
        when(conn.getCapability(InstrumentResolver.class)).thenReturn(Optional.of(resolver));

        BrokerHandle handle = new BrokerHandle(BrokerSource.DHAN, conn);
        BrokerInspectionReport report = BrokerExplorer.inspect(handle);

        assertEquals(BrokerSource.DHAN, report.source());
        assertTrue(report.capabilities().get("MarketDataProvider"));
        assertTrue(report.capabilities().get("OptionsProvider"));
        assertTrue(report.capabilities().get("PortfolioProvider"));
        assertTrue(report.capabilities().get("OrderQuery"));
        assertEquals(85000, report.instrumentCount());
        assertTrue(report.catalogLoaded());
    }

    @Test
    void inspectDetectsMissingCapabilities() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);
        when(resolver.catalogSize()).thenReturn(0);
        when(resolver.isLoaded()).thenReturn(false);
        when(conn.getCapability(any())).thenReturn(Optional.empty());
        when(conn.getCapability(InstrumentResolver.class)).thenReturn(Optional.of(resolver));

        BrokerHandle handle = new BrokerHandle(BrokerSource.SIMULATION, conn);
        BrokerInspectionReport report = BrokerExplorer.inspect(handle);

        assertFalse(report.capabilities().get("MarketDataProvider"));
        assertFalse(report.capabilities().get("OptionsProvider"));
        assertTrue(report.capabilities().get("InstrumentResolver")); // always present
        assertEquals(0, report.instrumentCount());
        assertFalse(report.catalogLoaded());
    }

    @Test
    void inspectDetectsCapabilityMarkers() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);
        when(resolver.catalogSize()).thenReturn(100);
        when(resolver.isLoaded()).thenReturn(true);
        when(conn.getCapability(any())).thenReturn(Optional.empty());
        when(conn.getCapability(InstrumentResolver.class)).thenReturn(Optional.of(resolver));

        BrokerHandle handle = new BrokerHandle(BrokerSource.DHAN, conn);
        BrokerInspectionReport report = BrokerExplorer.inspect(handle);

        // All 6 capability markers should be present in the report
        assertTrue(report.capabilities().containsKey("OptionsCapable"));
        assertTrue(report.capabilities().containsKey("FuturesCapable"));
        assertTrue(report.capabilities().containsKey("MarginCapable"));
        assertTrue(report.capabilities().containsKey("AlertCapable"));
        assertTrue(report.capabilities().containsKey("AdvancedOrderCapable"));
        assertTrue(report.capabilities().containsKey("NewsCapable"));
    }

    @Test
    void formatReportProducesReadableOutput() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);
        when(resolver.catalogSize()).thenReturn(1000);
        when(resolver.isLoaded()).thenReturn(true);
        when(conn.getCapability(any())).thenReturn(Optional.empty());
        when(conn.getCapability(InstrumentResolver.class)).thenReturn(Optional.of(resolver));

        BrokerHandle handle = new BrokerHandle(BrokerSource.DHAN, conn);
        BrokerInspectionReport report = BrokerExplorer.inspect(handle);

        String formatted = BrokerExplorer.formatReport(report);

        assertNotNull(formatted);
        assertTrue(formatted.contains("DHAN"));
        assertTrue(formatted.contains("Port Interfaces:"));
        assertTrue(formatted.contains("Capability Markers:"));
        assertTrue(formatted.contains("Metadata:"));
        assertTrue(formatted.contains("Summary:"));
    }

    @Test
    void inspectionReportCountsSupportedCapabilities() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);
        when(resolver.catalogSize()).thenReturn(100);
        when(resolver.isLoaded()).thenReturn(true);
        when(conn.getCapability(any())).thenReturn(Optional.empty());
        when(conn.getCapability(MarketDataProvider.class)).thenReturn(Optional.of(mock(MarketDataProvider.class)));
        when(conn.getCapability(OptionsProvider.class)).thenReturn(Optional.of(mock(OptionsProvider.class)));
        when(conn.getCapability(InstrumentResolver.class)).thenReturn(Optional.of(resolver));

        BrokerHandle handle = new BrokerHandle(BrokerSource.DHAN, conn);
        BrokerInspectionReport report = BrokerExplorer.inspect(handle);

        assertTrue(report.supportedCount() >= 3); // MarketData + Options + InstrumentResolver
        // 17 port interfaces (incl. CoverOrderProvider, MarketStatusProvider) + 6 capability markers
        assertEquals(23, report.totalCount());
    }
}
